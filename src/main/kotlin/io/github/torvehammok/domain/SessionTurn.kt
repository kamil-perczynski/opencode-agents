package io.github.torvehammok.domain

import io.github.torvehammok.infra.opencode.OpenCodeClientException
import io.github.torvehammok.infra.opencode.model.OCEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.util.concurrent.ConcurrentHashMap

private val log = LoggerFactory.getLogger(SessionTurn::class.java)

internal val COST_CONTEXT = MathContext(4, RoundingMode.HALF_UP)

internal sealed interface TurnOutcome {
    data class Succeeded(val text: String) : TurnOutcome
    data class Failed(val reason: String) : TurnOutcome
    data class Stalled(val silentForMs: Long) : TurnOutcome
}

/**
 * State of a single prompt round-trip on an opencode session.
 */
internal class SessionTurn(val sessionId: String) {

    var cost: BigDecimal = BigDecimal(0, COST_CONTEXT)
    var toolInvocations: Int = 0

    val connected = CompletableDeferred<Unit>()
    val outcome = CompletableDeferred<TurnOutcome>()

    @Volatile
    var promptSentAt: Long? = null

    @Volatile
    var lastProgressAt: Long = System.currentTimeMillis()

    @Volatile
    var lastHeartbeatAt: Long = System.currentTimeMillis()

    @Volatile
    var heartbeatsSeen: Boolean = false

    @Volatile
    var executionStarted: Boolean = false

    @Volatile
    var lastEventType: String? = null

    /** current event stream subscription - cancelled by the watchdog to force a reconnect */
    @Volatile
    var subscription: Job? = null

    private var lastText: String? = null

    // `session.tool.called` carries the input but not the name - it is only on `session.tool.input.started`
    private val toolNames = mutableMapOf<String, String>()

    // tool calls started but not finished yet - logged when the session stalls
    private val inFlightTools = ConcurrentHashMap<String, String>()

    fun inFlightTools(): Collection<String> = inFlightTools.values

    fun onConnected() {
        val now = System.currentTimeMillis()
        lastHeartbeatAt = now
        // events published while disconnected are lost - give the session a fresh inactivity window
        lastProgressAt = now
        connected.complete(Unit)
    }

    fun onHeartbeat() {
        heartbeatsSeen = true
        lastHeartbeatAt = System.currentTimeMillis()
    }

    fun complete(result: TurnOutcome) {
        outcome.complete(result)
    }

    fun fail(reason: String) {
        connected.completeExceptionally(OpenCodeClientException(reason))
        outcome.complete(TurnOutcome.Failed(reason))
    }

    fun lastText(): String? = lastText

    fun handle(event: OCEvent) {
        MDC.put("sessionId", sessionId)
        lastProgressAt = System.currentTimeMillis()
        lastEventType = event.type

        val data = event.data

        when (event.type) {
            "session.execution.started" -> executionStarted = true

            "session.tool.input.started" -> {
                log.info("opencode> tool={}", data?.name)

                val callId = data?.id
                val name = data?.name
                if (callId != null && name != null) {
                    toolNames[callId] = name
                    inFlightTools[callId] = name
                }
            }

            "session.tool.called" -> {
                toolInvocations++

                if (toolNames.remove(data?.id) == CODE_MODE_TOOL) {
                    val code = data?.input?.get("code") as? String ?: ""
                    val calls = extractCodeModeCalls(code)
                    calls.forEach { log.info("opencode> tool={} (via {})", it, CODE_MODE_TOOL) }

                    if (calls.isNotEmpty()) {
                        data?.id?.let { inFlightTools[it] = "$CODE_MODE_TOOL(${calls.joinToString()})" }
                    }
                }
            }

            "session.tool.success" -> data?.id?.let { inFlightTools.remove(it) }

            "session.tool.failed" -> {
                data?.id?.let { inFlightTools.remove(it) }
                log.info("opencode> tool failed: {}", data?.error?.message)
            }

            "session.text.ended" -> {
                log.info("opencode> text={}", data?.text)
                lastText = data?.text
            }

            "session.usage.updated" -> {
                // running total of the whole session
                if (data?.cost != null) {
                    cost = BigDecimal(data.cost, COST_CONTEXT)
                }
            }

            "session.step.failed" -> log.warn("opencode> step failed: {}", data?.error?.message)

            "session.execution.succeeded" -> {
                log.info("Session {} finished successfully", sessionId)
                complete(TurnOutcome.Succeeded(lastText ?: ""))
            }

            "session.execution.failed" -> complete(TurnOutcome.Failed("Session execution failed: ${data?.error?.message}"))

            "session.execution.interrupted" -> complete(TurnOutcome.Failed("Session execution interrupted: ${data?.reason}"))

            else -> log.trace("opencode> {}", event)
        }
    }
}
