package io.github.torvehammok

import io.github.torvehammok.dto.OCAgentStep
import io.github.torvehammok.dto.StepFinishPart
import io.github.torvehammok.dto.TextPart
import io.github.torvehammok.dto.ToolPart
import io.github.torvehammok.io.github.torvehammok.OCThread
import io.github.torvehammok.io.github.torvehammok.OcAgentResponse
import io.github.torvehammok.io.github.torvehammok.toXml
import org.slf4j.LoggerFactory
import tools.jackson.databind.json.JsonMapper
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.math.BigDecimal
import java.math.MathContext
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

private val log = LoggerFactory.getLogger(OcAgent::class.java)

class OcAgent(private val jsonMapper: JsonMapper) {

    private val model = "opencode-go/deepseek-v4-pro"

    fun run(conversation: OCThread): OcAgentResponse {
        val xml = toXml(conversation)

        val prompt = """
            ### TOOL GUIDELINES
            * **CLI**: You have authenticated access to github CLI (`gh`) as 'kamil-perczynski'. Access github resources through the CLI tool.
            * **ALLOWED Tools:** You may use the GitHub CLI (`gh`) and `curl`/webfetch tools for making HTTP requests.
            * **STRICTLY PROHIBITED:** You do not have general Bash access. The **usage of scripts is strictly forbidden**. Any attempt to use Node.js, Python, or other scripting languages will be flagged as an immediate failure.
            * **STRICTLY PROHIBITED:** You do not have access to the local filesystem. Any attempt to read/write files will be flagged as an immediate failure.
            
            ### RESPONSE GUIDELINES
            * Your response must be wrapped in <response> and </response> XML tags.
            
        """.trimIndent() + xml

        val startTime = System.currentTimeMillis()

        val builder = ProcessBuilder(
            "opencode",
            "run",
            "--model",
            model,
            "--dangerously-skip-permissions",
            "--agent",
            "slack-bot",
            "--format",
            "json"
        )

        builder.redirectErrorStream(true)
        val process = builder.start()


        thread(name = "ProcessInputWriter") {
            BufferedWriter(OutputStreamWriter(process.outputStream)).use { writer ->
                writer.write(prompt)
                writer.flush()
            }
        }

        var cost = BigDecimal(0, MathContext(6))
        var toolsInvocations = 0

        var agentResponse: String? = null

        val readerThread = thread(name = "ProcessOutputLogger") {
            try {
                process.inputReader().useLines { lines ->
                    lines.forEach { line ->
                        log.debug("[opencode]: {}", line)

                        val agentStep = jsonMapper.readValue(line, OCAgentStep::class.java)


                        if (agentStep.part is StepFinishPart) {
                            cost = cost.add(BigDecimal(agentStep.part.cost ?: 0.0, MathContext(6)))
                        } else if (agentStep.part is ToolPart) {
                            log.info("> ${agentStep.part.tool}: ${agentStep.part.state?.input?.command ?: agentStep.part.state?.input?.unknownFields}")
                            toolsInvocations += 1
                        } else if (agentStep.part is TextPart) {
                            val text = agentStep.part.text ?: ""

                            if (text.contains("<response>") && text.contains("</response>")) {
                                val startIdx = text.indexOf("<response>") + "<response>".length
                                val endIdx = text.indexOf("</response>")
                                val res = text.substring(startIdx, endIdx).trimIndent()
                                agentResponse = res
                            }

                        }

                    }
                }
            } catch (e: Exception) {
                log.debug("[opencode_err]: ${e.message}")
            }
        }


        val finished = process.waitFor(180.seconds.toJavaDuration())

        if (!finished) {
            log.debug("Process timed out! Force killing...")
            process.destroyForcibly()
            throw RuntimeException("Agent execution timed out")
        }

        val endTime = System.currentTimeMillis()
        return OcAgentResponse(
            response = agentResponse ?: "",
            cost = cost,
            toolsInvocations = toolsInvocations,
            duration = (endTime - startTime).milliseconds
        )
    }

}