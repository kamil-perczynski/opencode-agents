package io.github.torvehammok.domain

import io.github.torvehammok.infra.opencode.OpenCodeClient
import io.github.torvehammok.infra.opencode.dto.MessageTextPart
import io.github.torvehammok.infra.opencode.dto.OCAgentStep
import io.github.torvehammok.infra.opencode.dto.StepFinishPart
import io.github.torvehammok.infra.opencode.dto.ToolPart
import io.github.torvehammok.infra.OpencodeProps
import io.github.torvehammok.infra.opencode.dto.OcAgentResponse
import io.github.torvehammok.infra.opencode.OCThread
import io.github.torvehammok.infra.opencode.toXml
import kotlinx.coroutines.runBlocking
import org.koin.core.annotation.Singleton
import org.slf4j.LoggerFactory
import tools.jackson.databind.json.JsonMapper
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.math.BigDecimal
import java.math.MathContext
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

private val log = LoggerFactory.getLogger(OcAgent::class.java)

@Singleton
class OcAgent(
    private val jsonMapper: JsonMapper,
    private val openCodeClient: OpenCodeClient,
    private val opencodeProps: OpencodeProps
) {

    private val executorService = ThreadPoolExecutor(
        Runtime.getRuntime().availableProcessors(),
        Runtime.getRuntime().availableProcessors(),
        0L,
        TimeUnit.MILLISECONDS,
        LinkedBlockingQueue()

    )

    fun run(conversation: OCThread): OcAgentResponse {
        val prompt = opencodeProps.prompt.trimIndent() + "\n" + toXml(conversation)

        val startTime = System.currentTimeMillis()

        val cmd = mutableListOf(
            "opencode",
            "run",
            "--model",
            opencodeProps.model,
            "--agent",
            opencodeProps.agent,
            "--format",
            "json",
        )

        if (opencodeProps.attach) {
            cmd.add("--attach")
            cmd.add(opencodeProps.baseUrl)
        }

        log.info("Executing command: {}", cmd.joinToString(" "))

        val builder = ProcessBuilder(cmd)

        val env = builder.environment()
        if (opencodeProps.username != null) {
            env["OPENCODE_SERVER_USERNAME"] = opencodeProps.username
        }
        if (opencodeProps.password != null) {
            env["OPENCODE_SERVER_PASSWORD"] = opencodeProps.password
        }

        builder.redirectErrorStream(true)
        val process = builder.start()


        BufferedWriter(OutputStreamWriter(process.outputStream)).use { writer ->
            writer.write(prompt)
            writer.flush()
        }

        var cost = BigDecimal(0, MathContext(6))
        var toolsInvocations = 0

        var sessionId = ""

        executorService.run {
            try {
                process.inputReader().useLines { lines ->
                    lines.forEach { line ->
                        log.debug("[opencode]: {}", line)

                        val agentStep = jsonMapper.readValue(line, OCAgentStep::class.java)

                        sessionId = agentStep.sessionID ?: "<unknown>"

                        if (agentStep.part is StepFinishPart) {
                            cost = cost.add(BigDecimal(agentStep.part.cost ?: 0.0, MathContext(6)))
                        } else if (agentStep.part is ToolPart) {
                            log.info("> ${agentStep.part.tool}: ${agentStep.part.state?.input?.command ?: agentStep.part.state?.input?.unknownFields}")
                            toolsInvocations += 1
                        }
                    }
                }
            } catch (e: Exception) {
                log.debug("[opencode_err]: ${e.message}", e)
            }
        }

        val finished = process.waitFor(opencodeProps.maxAgentSessionDurationSeconds.seconds.toJavaDuration())

        if (!finished) {
            log.debug("Process timed out! Force killing...")
            process.destroyForcibly()
            throw RuntimeException("Agent execution timed out")
        }

        val endTime = System.currentTimeMillis()

        val msgs = runBlocking { openCodeClient.fetchSessionMessages(sessionId) }

        val response = msgs.last()
            .parts
            .find { it is MessageTextPart } as MessageTextPart?

        val agentResponse = toTextFromPart(response?.text)

        return OcAgentResponse(
            response = agentResponse,
            cost = cost,
            toolsInvocations = toolsInvocations,
            duration = (endTime - startTime).milliseconds
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