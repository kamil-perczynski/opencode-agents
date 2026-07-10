package io.github.torvehammok.domain

import io.github.ktor_batterypack.core.Closer
import io.github.torvehammok.domain.dto.*
import io.github.torvehammok.infra.opencode.OpenCodeClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.koin.core.annotation.Singleton
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toKotlinDuration

private val log = LoggerFactory.getLogger(OcAgent::class.java)

@Singleton
class OCServerAgent(private val openCodeClient: OpenCodeClient, private val opencodeProps: OpenCodeProps) {

    private val scope = CoroutineScope(Dispatchers.IO)

    suspend fun run(conversation: OCThread): OcAgentResponse {
        val prompt = opencodeProps.prompt.trimIndent() + "\n" + toXml(conversation)


        val t0 = System.currentTimeMillis()
        var totalCost = BigDecimal(0, MathContext(4, RoundingMode.HALF_UP))
        var toolsInvocations = 0

        val closer = Closer()

        val sessionId = openCodeClient.createSession()

        val job = scope.launch {
            openCodeClient.subscribeSessionEvents(sessionId) { data ->
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
                                closer.close()
                                return@subscribeSessionEvents
                            }

                            if (cost != null) {
                                totalCost = totalCost.plus(BigDecimal(cost))
                            }

                            when (type) {
                                "tool" if toolState?.status == "running" -> {
                                    log.info("opencode> {}, state={}", tool, toolState)
                                    toolsInvocations++
                                }
                                "text" -> log.info("opencode> {}, text={}", type, text)
                                "reasoning" -> log.info("opencode> reasoning")
                                else -> log.trace("opencode> {}", data.payload.properties)
                            }
                        }

                        "message.part.delta" -> {} // ignore
                    }
                } catch (ex: CancellationException) {
                    log.info("Session {} finished successfullly", sessionId)
                    return@subscribeSessionEvents
                } catch (ex: Exception) {
                    log.warn("Failed to deserialize OCAgentStep", ex)
                }
            }
        }
        closer.add { job.cancel() }

        openCodeClient.promptAsync(
            agent = opencodeProps.agent,
            prompt = prompt,
            sessionId = sessionId,
            modelId = opencodeProps.model
        )

        withTimeout(opencodeProps.maxAgentSessionDurationSeconds.seconds) { job.join() }

        val msgs = openCodeClient.fetchSessionMessages(sessionId)
        val response = msgs.last()
            .parts
            .find { it is MessageTextPart } as MessageTextPart?

        val agentResponse = toTextFromPart(response?.text)

        return OcAgentResponse(
            response = agentResponse,
            cost = totalCost,
            toolsInvocations = toolsInvocations,
            duration = Duration.ofMillis(System.currentTimeMillis() - t0).toKotlinDuration()
        )
    }

}

private fun toTextFromPart(partText: String?): String {
    val text = partText ?: ""

    if (text.contains("<response>") || text.contains("</response>")) {
        var startIdx = 0
        var endIdx = text.length

        val startTagIndex = text.indexOf("<response>")
        val endTagIndex = text.indexOf("</response>")

        if (startTagIndex != -1) {
            startIdx = startTagIndex + "<response>".length
        }
        if (endTagIndex != -1) {
            endIdx = endTagIndex
        }

        val res = text.substring(startIdx, endIdx).trimIndent()
        return res
    }

    return text
}