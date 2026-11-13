package io.github.torvehammok.domain

import io.github.torvehammok.domain.dto.*
import io.github.torvehammok.infra.opencode.OpenCodeClient
import io.github.torvehammok.infra.opencode.OpenCodeClientException
import io.github.torvehammok.infra.opencode.OpenCodeHealth
import io.github.torvehammok.infra.opencode.OpenCodeSessionNotFoundException
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
private const val RESUMABLE_TIMEOUT_RESPONSE = """⏳ I ran out of time and paused before finishing.

Everything I have found so far is kept. Mention me in this thread with `continue` to pick up where I left off, or ask a narrower question to get a faster answer."""
private const val BROKEN_RESPONSE = "Oops! The agent has broken down. Rest assured - we will fix it. Just try again later."
private const val STALLED_RESPONSE = "The agent got stuck and has been stopped. Try again in a minute."

// right after a prompt is admitted the session may not be listed as active yet
private const val RECONCILE_GRACE_MS = 5_000L

@Singleton
class OCServerAgent(
    private val openCodeClient: OpenCodeClient,
    private val opencodeProps: OpenCodeProps,
    private val openCodeHealth: OpenCodeHealth,
    private val modelPricingProvider: ModelPricingProvider
) {

    private val watchdogProps get() = opencodeProps.watchdog

    /**
     * Runs the agent on the thread, reusing the thread's [previous] session when possible - then only
     * the messages it has not seen yet are sent. The returned session is null when it must not be reused.
     */
    suspend fun run(conversation: OCThread, previous: ThreadSession?): OcAgentRun {
        val t0 = System.currentTimeMillis()

        var turn = SessionTurn(sessionId = previous?.sessionId ?: "<session_id>")

        try {
            val pricing = modelPricingProvider.findPricing(opencodeProps.model)
            turn = openTurn(previous, pricing)

            val outcome = withTimeout(opencodeProps.maxAgentSessionDurationSeconds.seconds) {
                try {
                    runTurn(turn, toPrompt(conversation, previous, turn.reused))
                } catch (ex: OpenCodeSessionNotFoundException) {
                    if (!turn.reused) {
                        throw ex
                    }
                    log.warn("Session {} no longer exists, starting a new one", turn.sessionId)
                    turn = newTurn(pricing)
                    runTurn(turn, toPrompt(conversation, previous = null, reused = false))
                }
            }

            log.info("Session {} turn finished, reused={}, outcome={}", turn.sessionId, turn.reused, outcome::class.simpleName)

            return when (outcome) {
                is TurnOutcome.Succeeded -> OcAgentRun(
                    response = toResponse(turn, outcome.text, t0),
                    session = toThreadSession(turn, conversation)
                )

                is TurnOutcome.Failed -> {
                    log.warn("Session {} has failed: {}", turn.sessionId, outcome.reason)
                    OcAgentRun(toResponse(turn, BROKEN_RESPONSE, t0), session = null)
                }

                is TurnOutcome.Stalled -> {
                    log.error(
                        "Session {} has stalled: no events for {} ms, last event={}, in-flight tools={}. Restarting opencode server.",
                        turn.sessionId, outcome.silentForMs, turn.lastEventType, turn.inFlightTools()
                    )
                    interruptQuietly(turn.sessionId)
                    delay(watchdogProps.checkIntervalMs)
                    val stillActive = fetchActiveSessionIdsOrNull()?.contains(turn.sessionId)
                    if (stillActive == true) {
                        openCodeHealth.reportStall(turn.sessionId)
                    } else {
                        log.warn("Session {} is no longer active after interrupt, skipping the server restart", turn.sessionId)
                    }
                    OcAgentRun(toResponse(turn, STALLED_RESPONSE, t0), session = null)
                }
            }
        } catch (ex: TimeoutCancellationException) {
            log.info("Session {} has timed out. {}", turn.sessionId, ex.message)
            interruptQuietly(turn.sessionId)

            // the interrupted session keeps its work - the next message in the thread continues it
            // (a session still running despite the interrupt is replaced by a new one, see openTurn)
            if (turn.promptSentAt == null) {
                return OcAgentRun(toResponse(turn, TIMEOUT_RESPONSE, t0), session = null)
            }
            return OcAgentRun(toResponse(turn, RESUMABLE_TIMEOUT_RESPONSE, t0), session = toThreadSession(turn, conversation))
        } catch (ex: OpenCodeClientException) {
            log.warn("Session {} has been aborted due to opencode server error: {}", turn.sessionId, ex.message, ex)

            return OcAgentRun(toResponse(turn, BROKEN_RESPONSE, t0), session = null)
        }
    }

    /**
     * A session still running (e.g. its interrupt has failed) must not be prompted - the prompt would steer
     * that execution instead of starting a new one.
     */
    private suspend fun openTurn(previous: ThreadSession?, pricing: ModelPricing?): SessionTurn {
        if (previous == null) {
            return newTurn(pricing)
        }

        val active = fetchActiveSessionIdsOrNull()
        if (active != null && previous.sessionId !in active) {
            log.info("Reusing session {} of the thread", previous.sessionId)
            return SessionTurn(
                sessionId = previous.sessionId,
                baseCost = previous.costTotal,
                reused = true,
                pricing = pricing
            )
        }

        log.warn("Session {} of the thread is still running or its state is unknown, starting a new one", previous.sessionId)
        return newTurn(pricing)
    }

    private suspend fun newTurn(pricing: ModelPricing?): SessionTurn {
        val sessionId = openCodeClient.createSession(agent = opencodeProps.agent, modelId = opencodeProps.model)
        log.info("Created session {}", sessionId)
        return SessionTurn(sessionId = sessionId, pricing = pricing)
    }

    private fun toPrompt(conversation: OCThread, previous: ThreadSession?, reused: Boolean): String {
        if (reused && previous != null) {
            return toXml(conversation.copy(messages = messagesAfter(conversation, previous.lastTs)))
        }
        return opencodeProps.prompt.trimIndent() + "\n" + toXml(conversation)
    }

    private suspend fun runTurn(turn: SessionTurn, prompt: String): TurnOutcome = coroutineScope {
        try {
            launch { pumpEvents(turn) }

            // the global event bus is volatile - prompt only once the subscription is live
            withTimeoutOrNull(watchdogProps.eventStreamConnectTimeoutMs) { turn.connected.await() }
                ?: throw OpenCodeClientException("opencode event stream did not connect in time")

            turn.promptMessageId = openCodeClient.prompt(sessionId = turn.sessionId, prompt = prompt)

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
 * Only messages newer than this turn's prompt count - older ones belong to previous turns of a reused session.
 */
private fun toOutcome(messages: List<OCSessionMessage>, turn: SessionTurn): TurnOutcome {
    val promptMessageId = turn.promptMessageId
    val turnMessages = if (promptMessageId != null) messages.takeWhile { it.id != promptMessageId } else messages

    val idle = turnMessages.firstOrNull { it.type == "idle" }
        ?: return TurnOutcome.Failed("Session is idle, but the outcome of this turn is unknown")

    if (idle.outcome != "succeeded") {
        return TurnOutcome.Failed("Session execution ended with outcome ${idle.outcome}")
    }

    val text = turnMessages.firstOrNull { it.type == "assistant" }
        ?.content
        ?.lastOrNull { it.type == "text" }
        ?.text

    return TurnOutcome.Succeeded(text ?: turn.lastText() ?: "")
}

private fun toThreadSession(turn: SessionTurn, conversation: OCThread): ThreadSession {
    return ThreadSession(
        sessionId = turn.sessionId,
        lastTs = latestTs(conversation) ?: conversation.threadTs,
        costTotal = turn.cost
    )
}

private fun toResponse(turn: SessionTurn, response: String, startTimeMillis: Long): OcAgentResponse {
    return OcAgentResponse(
        response = response,
        cost = turn.turnCost(),
        toolsInvocations = turn.toolInvocations,
        duration = toSessionDuration(startTimeMillis)
    )
}

private fun toSessionDuration(startTimeMillis: Long): kotlin.time.Duration {
    return Duration.ofMillis(System.currentTimeMillis() - startTimeMillis).toKotlinDuration()
}
