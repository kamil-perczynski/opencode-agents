package io.github.torvehammok

import io.github.torvehammok.dto.OCAgentStep
import io.github.torvehammok.dto.StepFinishPart
import io.github.torvehammok.dto.TextPart
import io.github.torvehammok.dto.ToolPart
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.math.BigDecimal
import java.math.MathContext
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

private val log = LoggerFactory.getLogger(PrintResponseTest::class.java)

class PrintResponseTest {

    companion object {
        val jsonMapper = JsonMapperFactory.createJsonMapper()
    }

    @Test
    fun nam2e() {
        val prompt = """
        You are slack bot helping developers. Your task is to provide helpful responses to user queries in a Slack channel.
        
        Rules: 
        - You have only two minutes to answer, so NO NOT OVERTHINK.
        - DO NOT GET STUCK ON EXPLORATION. FORMULATE THE ANSWER QUICKLY BASE ON NO MORE THAN 10 WEBFETCHES OR API CALLS.
        - DO NOT TRY TO WORK AROUND FAILED TOOL EXECUTION MORE THAN 3 TIMES. JUST MOVE ON.
        - It's better to communicate what is missing something than to miss the deadline.
        - USAGE OF SCRIPTS IS STRICTLY PROHIBITED. ANY USAGE OF NODE, PYTHON OR OTHER SCRIPTS WILL BE CONSIDERED AS A FAILURE TO ANSWER.
        
        You have access to the following tools:
        - github through github cli
        - curl and webfetch tool for making http requests
        
        You DO NOT have access to:
        - bash except for github cli and curl
        
        Response:
        - Use emojis and be playful in your responses.
        - resulting message should be wrapped in <response> xml tags
        
        <user_message>
        Which aws spot instances shutdown events does the https://github.com/kamil-perczynski/kube-spot-operator
        support?
        Is there anything missing? 
        </user_message>
    """.trimIndent()


        val builder = ProcessBuilder(
            "opencode",
            "run",
            "--model",
            "opencode-go/deepseek-v4-pro",
            "--dangerously-skip-permissions",
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
//                        log.debug("[opencode]: {}", line)
                        val agentStep = jsonMapper.readValue(line, OCAgentStep::class.java)

                        if (agentStep.part is StepFinishPart) {
                            cost = cost.add(BigDecimal(agentStep.part.cost ?: 0.0, MathContext(6)))
                        } else if (agentStep.part is ToolPart) {
                            log.info("> ${agentStep.part.tool}: ${agentStep.part.state?.input?.command ?: agentStep.part.state?.input?.unknownFields}")
                            toolsInvocations += 1
                        } else if (agentStep.part is TextPart) {
                            val text = agentStep.part.text ?: ""

                            if (text.contains("<response>") && line.contains("</response>")) {
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
        }

        log.info("Process finished with exit code ${process.exitValue()}, total cost: ${cost.toPlainString()}, tools invocations: $toolsInvocations")
        log.info("Agent response:\n$agentResponse")

        readerThread.join(2000)
    }
}