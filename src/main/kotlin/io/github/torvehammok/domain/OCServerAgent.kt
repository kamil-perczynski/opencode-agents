package io.github.torvehammok.domain

import io.github.torvehammok.domain.dto.*
import io.github.torvehammok.infra.opencode.OpenCodeClient
import io.github.torvehammok.infra.opencode.OpenCodeClientException
import io.github.torvehammok.infra.opencode.OpenCodeHealth
import io.github.torvehammok.infra.opencode.model.OCSessionMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.annotation.Singleton
import org.slf4j.LoggerFactory
import java.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toKotlinDuration

private val log = LoggerFactory.getLogger(OCServerAgent::class.java)

private const val TIMEOUT_RESPONSE = "The agent did not manage to respond in time. Try again later."
private const val BROKEN_RESPONSE = "Oops! The agent has broken down. Rest assured - we will fix it. Just try again later."
private const val STALLED_RESPONSE = "The agent got stuck and has been stopped. Try again in a minute."

// right after a prompt is admitted the session may not be listed as active yet
private const val RECONCILE_GRACE_MS = 5_000L

@Singleton
class OCServerAgent(
    private val openCodeClient: OpenCodeClient,
    private val opencodeProps: OpenCodeProps,
    private val openCodeHealth: OpenCodeHealth
) {

    private val watchdogProps get() = opencodeProps.watchdog

    suspend fun run(conversation: OCThread): OcAgentResponse {
        val prompt = opencodeProps.prompt.trimIndent() + "\n" + toXml(conversation)

        val t0 = System.currentTimeMillis()

        var turn = SessionTurn(sessionId = "<session_id>")

        try {
            turn = SessionTurn(
                sessionId = openCodeClient.createSession(agent = opencodeProps.agent, modelId = opencodeProps.model)
            )

            val outcome = withTimeout(opencodeProps.maxAgentSessionDurationSeconds.seconds) {
                runTurn(turn, prompt)
            }

            return when (outcome) {
                is TurnOutcome.Succeeded -> turn.toResponse(outcome.text, t0)

                is TurnOutcome.Failed -> {
                    log.warn("Session {} has failed: {}", turn.sessionId, outcome.reason)
                    turn.toResponse(BROKEN_RESPONSE, t0)
                }

                is TurnOutcome.Stalled -> {
                    log.error(
                        "Session {} has stalled: no events for {} ms, last event={}, in-flight tools={}. Restarting opencode server.",
                        turn.sessionId, outcome.silentForMs, turn.lastEventType, turn.inFlightTools()
                    )
                    interruptQuietly(turn.sessionId)
                    openCodeHealth.reportStall(turn.sessionId)
                    turn.toResponse(STALLED_RESPONSE, t0)
                }
            }
        } catch (ex: TimeoutCancellationException) {
            log.info("Session {} has timed out. {}", turn.sessionId, ex.message)
            interruptQuietly(turn.sessionId)

            return turn.toResponse(TIMEOUT_RESPONSE, t0)
        } catch (ex: OpenCodeClientException) {
            log.warn("Session {} has been aborted due to opencode server error: {}", turn.sessionId, ex.message, ex)

            return turn.toResponse(BROKEN_RESPONSE, t0)
        }
    }

    private suspend fun runTurn(turn: SessionTurn, prompt: String): TurnOutcome = coroutineScope {
        try {
            launch { pumpEvents(turn) }

            // the global event bus is volatile - prompt only once the subscription is live
            withTimeoutOrNull(watchdogProps.eventStreamConnectTimeoutMs) { turn.connected.await() }
                ?: throw OpenCodeClientException("opencode event stream did not connect in time")

            openCodeClient.prompt(sessionId = turn.sessionId, prompt = prompt)

            val now = System.currentTimeMillis()
            turn.promptSentAt = now
            turn.lastProgressAt = now

            launch { watch(turn) }

            turn.outcome.await()
        } finally {
            coroutineContext.cancelChildren()
        }
    }

    /**
     * Keeps the event stream subscribed until the turn completes. Every reconnect may have missed events,
     * so the session state is reconciled afterward.
     */
    private suspend fun pumpEvents(turn: SessionTurn): Unit = coroutineScope {
        var connections = 0
        var failedAttempts = 0

        while (isActive && !turn.outcome.isCompleted) {
            if (!openCodeHealth.isServerAvailable) {
                turn.fail("opencode server is ${openCodeHealth.processState.name.lowercase()}")
                return@coroutineScope
            }

            var connectedNow = false

            val subscription = launch {
                try {
                    openCodeClient.subscribeSessionEvents(
                        sessionId = turn.sessionId,
                        onConnected = {
                            connectedNow = true
                            turn.onConnected()

                            if (connections++ > 0) {
                                log.info("Session {} event stream reconnected, reconciling missed events", turn.sessionId)
                                launch { reconcile(turn) }
                            }
                        },
                        onHeartbeat = { turn.onHeartbeat() }
                    ) { event -> turn.handle(event) }

                    if (!turn.outcome.isCompleted) {
                        log.warn("Session {} event stream closed by server", turn.sessionId)
                    }
                } catch (ex: OpenCodeClientException) {
                    log.warn("Session {} event stream failed: {}", turn.sessionId, ex.message)
                }
            }
            turn.subscription = subscription
            subscription.join()

            if (turn.outcome.isCompleted) {
                return@coroutineScope
            }

            failedAttempts = if (connectedNow) 1 else failedAttempts + 1

            if (failedAttempts > watchdogProps.maxReconnectAttempts) {
                turn.fail("opencode event stream lost after ${watchdogProps.maxReconnectAttempts} reconnection attempts")
                return@coroutineScope
            }

            delay(reconnectBackoff(failedAttempts))
        }
    }

    private suspend fun watch(turn: SessionTurn) = coroutineScope {
        while (isActive && !turn.outcome.isCompleted) {
            delay(watchdogProps.checkIntervalMs)

            val now = System.currentTimeMillis()
            val silentFor = now - turn.lastProgressAt

            if (silentFor < watchdogProps.inactivityTimeoutMs) {
                continue
            }

            // no heartbeats = the connection is dead, not the session - reconnect and judge again later
            if (turn.heartbeatsSeen && now - turn.lastHeartbeatAt > watchdogProps.heartbeatTimeoutMs) {
                log.warn("Session {} event stream has no heartbeat for {} ms, reconnecting", turn.sessionId, now - turn.lastHeartbeatAt)
                turn.subscription?.cancel()
                continue
            }

            val active = fetchActiveSessionIdsOrNull() ?: continue

            if (turn.sessionId in active) {
                turn.complete(TurnOutcome.Stalled(silentFor))
            } else {
                reconcile(turn)
            }
        }
    }

    /**
     * Completes the turn from the session messages if the session has finished while its terminal event was missed.
     */
    private suspend fun reconcile(turn: SessionTurn) {
        val promptSentAt = turn.promptSentAt ?: return

        if (!turn.executionStarted && System.currentTimeMillis() - promptSentAt < RECONCILE_GRACE_MS) {
            return
        }

        val active = fetchActiveSessionIdsOrNull() ?: return
        if (turn.sessionId in active) {
            return
        }

        try {
            val outcome = toOutcome(openCodeClient.fetchMessages(turn.sessionId), turn)
            log.info("Session {} finished while its events were missed, recovered outcome: {}", turn.sessionId, outcome::class.simpleName)
            turn.complete(outcome)
        } catch (ex: OpenCodeClientException) {
            log.warn("Session {} could not be reconciled: {}", turn.sessionId, ex.message)
        }
    }

    private suspend fun fetchActiveSessionIdsOrNull(): Set<String>? {
        return try {
            openCodeClient.fetchActiveSessionIds()
        } catch (ex: OpenCodeClientException) {
            log.warn("Failed to fetch active opencode sessions: {}", ex.message)
            null
        }
    }

    private fun reconnectBackoff(attempt: Int): Long {
        val backoff = watchdogProps.reconnectBackoffMs shl (attempt - 1).coerceIn(0, 20)
        return backoff.coerceAtMost(watchdogProps.reconnectBackoffMaxMs)
    }

    private suspend fun interruptQuietly(sessionId: String) {
        try {
            withContext(NonCancellable) {
                withTimeout(watchdogProps.eventStreamConnectTimeoutMs) {
                    openCodeClient.interrupt(sessionId)
                }
            }
        } catch (ex: CancellationException) {
            log.warn("Interrupting session {} has timed out", sessionId)
        } catch (ex: Exception) {
            log.warn("Failed to interrupt session {}: {}", sessionId, ex.message)
        }
    }

}

/**
 * Messages are newest first: the `idle` entry closes the last execution, preceded by the final `assistant` message.
 */
private fun toOutcome(messages: List<OCSessionMessage>, turn: SessionTurn): TurnOutcome {
    val idle = messages.firstOrNull { it.type == "idle" }
        ?: return TurnOutcome.Failed("Session is idle, but its outcome is unknown")

    if (idle.outcome != "succeeded") {
        return TurnOutcome.Failed("Session execution ended with outcome ${idle.outcome}")
    }

    val text = messages.firstOrNull { it.type == "assistant" }
        ?.content
        ?.lastOrNull { it.type == "text" }
        ?.text

    return TurnOutcome.Succeeded(text ?: turn.lastText() ?: "")
}

private fun SessionTurn.toResponse(response: String, startTimeMillis: Long): OcAgentResponse {
    return OcAgentResponse(
        response = response,
        cost = cost,
        toolsInvocations = toolInvocations,
        duration = toSessionDuration(startTimeMillis)
    )
}

private fun toSessionDuration(startTimeMillis: Long): kotlin.time.Duration {
    return Duration.ofMillis(System.currentTimeMillis() - startTimeMillis).toKotlinDuration()
}
