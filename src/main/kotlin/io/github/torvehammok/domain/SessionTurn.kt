package io.github.torvehammok.domain

import io.github.torvehammok.infra.opencode.OpenCodeClientException
import io.github.torvehammok.infra.opencode.model.OCEvent
import io.github.torvehammok.infra.opencode.model.OCTokenUsage
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

private fun toTokenUsage(tokens: OCTokenUsage): TokenUsage {
    return TokenUsage(
        input = tokens.input,
        output = tokens.output,
        reasoning = tokens.reasoning,
        cacheRead = tokens.cache?.read ?: 0,
        cacheWrite = tokens.cache?.write ?: 0
    )
}

internal sealed interface TurnOutcome {
    data class Succeeded(val text: String) : TurnOutcome
    data class Failed(val reason: String) : TurnOutcome
    data class Stalled(val silentForMs: Long) : TurnOutcome
}

/**
 * State of a single prompt round-trip on an opencode session.
 */
internal class SessionTurn(
    val sessionId: String,
    /** session cost before this turn - opencode reports a running total of the whole session */
    private val baseCost: BigDecimal = BigDecimal.ZERO,
    /** session kept from a previous turn of the thread */
    val reused: Boolean = false,
    /** list price of the session model, to estimate the cost when the provider reports none */
    private val pricing: ModelPricing? = null
) {

    /** running cost of the whole session */
    var cost: BigDecimal = baseCost
    var toolInvocations: Int = 0

    /** user message created by this turn's prompt - older messages belong to previous turns */
    @Volatile
    var promptMessageId: String? = null

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

    fun turnCost(): BigDecimal = (cost - baseCost).max(BigDecimal.ZERO)

    /**
     * Since opencode 2.2 the cost is the one reported by the provider - subscription plans (e.g. OpenCode Go)
     * report 0, then the cost is estimated from the tokens at list price.
     */
    private fun sessionCost(reported: Double?, tokens: OCTokenUsage?): BigDecimal? {
        if (reported != null && reported > 0) {
            return BigDecimal(reported, COST_CONTEXT)
        }
        if (pricing != null && tokens != null) {
            return pricing.costOf(toTokenUsage(tokens)).round(COST_CONTEXT)
        }
        return reported?.let { BigDecimal(it, COST_CONTEXT) }
    }

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

            // running totals of the whole session
            "session.usage.updated" -> cost = sessionCost(data?.cost, data?.tokens) ?: cost

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
