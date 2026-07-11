package io.github.torvehammok.domain

import io.github.torvehammok.domain.dto.*
import io.github.torvehammok.infra.opencode.OpenCodeClient
import io.github.torvehammok.infra.opencode.OpenCodeClientException
import io.github.torvehammok.infra.opencode.model.OCSseEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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

        try {
            sub = sub.copy(sessionId = openCodeClient.createSession())
            subscribeToOpenCodeSSE(sub)

            openCodeClient.promptAsync(
                agent = opencodeProps.agent,
                prompt = prompt,
                sessionId = sub.sessionId,
                modelId = opencodeProps.model
            )

            val agentResponse = withTimeout(opencodeProps.maxAgentSessionDurationSeconds.seconds) {
                sub.response.await()
            }

            return agentResponse.copy(duration = toSessionDuration(t0))
        } catch (ex: TimeoutCancellationException) {
            log.info("Session {} has timed out. {}", sub.sessionId, ex.message)

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
        }
    }

    private fun subscribeToOpenCodeSSE(sub: OcAgenticSessionSub): OcAgenticSessionSub {
        var lastTextMessage: OCSseEvent? = null

        scope.launch {
            openCodeClient.subscribeSessionEvents(sub.sessionId) { data ->
                MDC.put("sessionId", sub.sessionId)

                try {
                    when (data.payload?.type) {
                        "message.updated", "message.part.updated" -> {
                            val type = data.payload.properties?.part?.type
                            val tool = data.payload.properties?.part?.tool
                            val toolState = data.payload.properties?.part?.state
                            val cost = data.payload.properties?.part?.cost
                            val text = data.payload.properties?.part?.text
                            val info = data.payload.properties?.info

                            if (info?.finish == "stop") {
                                log.info("Session {} finished successfully", sub.sessionId)

                                val response = OcAgentResponse(
                                    response = lastTextMessage?.payload?.properties?.part?.text ?: "",
                                    cost = sub.cost,
                                    toolsInvocations = sub.toolInvocations,
                                    duration = Duration.ofMillis(0L).toKotlinDuration()
                                )

                                sub.response.complete(response)
                                this.cancel()
                                return@subscribeSessionEvents
                            }

                            if (cost != null) {
                                sub.cost = sub.cost.plus(BigDecimal(cost))
                            }

                            when (type) {
                                "tool" if toolState?.status == "running" -> {
                                    log.info("opencode> {}, state={}", tool, toolState)
                                    sub.toolInvocations++
                                }

                                "text" -> {
                                    log.info("opencode> {}, text={}", type, text)
                                    lastTextMessage = data
                                }

                                "reasoning" -> log.debug("opencode> reasoning")
                                else -> log.trace("opencode> {}", data.payload.properties)
                            }
                        }

                        else -> log.trace("Event: {}", data)
                    }
                } catch (ex: CancellationException) {
                    log.warn("Session {} finished hit a timeout", sub.sessionId, ex)
                    sub.response.completeExceptionally(ex)
                    return@subscribeSessionEvents
                } catch (ex: Exception) {
                    sub.response.completeExceptionally(ex)
                    this.cancel()
                }
            }
        }

        return sub
    }

}

private fun toSessionDuration(startTimeMillis: Long): kotlin.time.Duration {
    return Duration.ofMillis(System.currentTimeMillis() - startTimeMillis).toKotlinDuration()
}

private data class OcAgenticSessionSub(
    val sessionId: String,
    var cost: BigDecimal = BigDecimal(0, MathContext(4, RoundingMode.HALF_UP)),
    var toolInvocations: Int = 0,
    val response: CompletableDeferred<OcAgentResponse> = CompletableDeferred()
)
