package io.github.torvehammok.domain

import io.github.torvehammok.domain.dto.*
import io.github.torvehammok.infra.opencode.OpenCodeClient
import io.github.torvehammok.infra.opencode.OpenCodeClientException
import io.github.torvehammok.infra.opencode.model.OCEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.koin.core.annotation.Singleton
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toKotlinDuration

private val log = LoggerFactory.getLogger(OCServerAgent::class.java)

@Singleton
class OCServerAgent(private val openCodeClient: OpenCodeClient, private val opencodeProps: OpenCodeProps) {

    private val scope = CoroutineScope(Dispatchers.IO + CoroutineName("OCServerAgent"))

    suspend fun run(conversation: OCThread): OcAgentResponse {
        val prompt = opencodeProps.prompt.trimIndent() + "\n" + toXml(conversation)

        val t0 = System.currentTimeMillis()

        var sub = OcAgenticSessionSub(sessionId = "<session_id>") // mutable object
        var subscription: Job? = null

        try {
            sub = sub.copy(
                sessionId = openCodeClient.createSession(agent = opencodeProps.agent, modelId = opencodeProps.model)
            )

            val agentResponse = withTimeout(opencodeProps.maxAgentSessionDurationSeconds.seconds) {
                subscription = subscribeToOpenCodeSSE(sub)

                // the global event bus is volatile - prompt only once the subscription is live
                sub.connected.await()

                openCodeClient.prompt(sessionId = sub.sessionId, prompt = prompt)

                sub.response.await()
            }

            return agentResponse.copy(duration = toSessionDuration(t0))
        } catch (ex: TimeoutCancellationException) {
            log.info("Session {} has timed out. {}", sub.sessionId, ex.message)
            interruptQuietly(sub.sessionId)

            return OcAgentResponse(
                response = "The agent did not manage to respond in time. Try again later.",
                cost = sub.cost,
                toolsInvocations = sub.toolInvocations,
                duration = toSessionDuration(t0)
            )
        } catch (ex: OpenCodeClientException) {
            log.warn("Session {} has been aborted due to opencode server error: {}", sub.sessionId, ex.message, ex)

            return OcAgentResponse(
                response = "Oops! The agent has broken down. Rest assured - we will fix it. Just try again later.",
                cost = sub.cost,
                toolsInvocations = sub.toolInvocations,
                duration = toSessionDuration(t0)
            )
        } finally {
            subscription?.cancel()
        }
    }

    private fun subscribeToOpenCodeSSE(sub: OcAgenticSessionSub): Job {
        var lastText: String? = null

        return scope.launch {
            try {
                openCodeClient.subscribeSessionEvents(
                    sessionId = sub.sessionId,
                    onConnected = { sub.connected.complete(Unit) }
                ) { event ->
                    MDC.put("sessionId", sub.sessionId)
                    val data = event.data

                    when (event.type) {
                        "session.tool.input.started" -> log.info("opencode> tool={}", data?.name)

                        "session.tool.called" -> sub.toolInvocations++

                        "session.tool.failed" -> log.info("opencode> tool failed: {}", data?.error?.message)

                        "session.text.ended" -> {
                            log.info("opencode> text={}", data?.text)
                            lastText = data?.text
                        }

                        "session.usage.updated" -> {
                            // running total of the whole session
                            if (data?.cost != null) {
                                sub.cost = BigDecimal(data.cost, COST_CONTEXT)
                            }
                        }

                        "session.step.failed" -> log.warn("opencode> step failed: {}", data?.error?.message)

                        "session.execution.succeeded" -> {
                            log.info("Session {} finished successfully", sub.sessionId)

                            sub.response.complete(
                                OcAgentResponse(
                                    response = lastText ?: "",
                                    cost = sub.cost,
                                    toolsInvocations = sub.toolInvocations,
                                    duration = Duration.ofMillis(0L).toKotlinDuration()
                                )
                            )
                        }

                        "session.execution.failed" -> sub.response.completeExceptionally(
                            OpenCodeClientException("Session execution failed: ${data?.error?.message}")
                        )

                        "session.execution.interrupted" -> sub.response.completeExceptionally(
                            OpenCodeClientException("Session execution interrupted: ${data?.reason}")
                        )

                        else -> log.trace("opencode> {}", event)
                    }
                }

                sub.failIfPending(OpenCodeClientException("opencode event stream has ended unexpectedly"))
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                sub.failIfPending(OpenCodeClientException("opencode event stream has failed: ${ex.message}", ex))
            }
        }
    }

    private suspend fun interruptQuietly(sessionId: String) {
        try {
            withContext(NonCancellable) {
                openCodeClient.interrupt(sessionId)
            }
        } catch (ex: Exception) {
            log.warn("Failed to interrupt session {}: {}", sessionId, ex.message)
        }
    }

}

private val COST_CONTEXT = MathContext(4, RoundingMode.HALF_UP)

private fun toSessionDuration(startTimeMillis: Long): kotlin.time.Duration {
    return Duration.ofMillis(System.currentTimeMillis() - startTimeMillis).toKotlinDuration()
}

private data class OcAgenticSessionSub(
    val sessionId: String,
    var cost: BigDecimal = BigDecimal(0, COST_CONTEXT),
    var toolInvocations: Int = 0,
    val connected: CompletableDeferred<Unit> = CompletableDeferred(),
    val response: CompletableDeferred<OcAgentResponse> = CompletableDeferred()
) {
    fun failIfPending(ex: Exception) {
        connected.completeExceptionally(ex)
        response.completeExceptionally(ex)
    }
}
