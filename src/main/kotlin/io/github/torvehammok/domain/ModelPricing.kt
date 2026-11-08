package io.github.torvehammok.domain

import java.math.BigDecimal
import java.math.MathContext

private val TOKENS_PER_MILLION = BigDecimal(1_000_000)

/**
 * Port to the model price list, used to estimate session cost when the provider does not report one
 * (e.g. subscription plans reporting a cost of 0).
 */
interface ModelPricingProvider {

    /**
     * @param modelId model in format `<provider>/<model>`, as in [OpenCodeProps.model]
     * @return list price of the model, null when unknown
     */
    suspend fun findPricing(modelId: String): ModelPricing?

}

/**
 * List price of a model, in USD per million tokens.
 */
data class ModelPricing(
    val input: BigDecimal,
    val output: BigDecimal,
    val cacheRead: BigDecimal,
    val cacheWrite: BigDecimal
) {

    fun costOf(tokens: TokenUsage): BigDecimal {
        val cost = input * BigDecimal(tokens.input) +
            output * BigDecimal(tokens.output + tokens.reasoning) +
            cacheRead * BigDecimal(tokens.cacheRead) +
            cacheWrite * BigDecimal(tokens.cacheWrite)

        return cost.divide(TOKENS_PER_MILLION, MathContext.DECIMAL64)
    }

}

/**
 * Tokens used by a session. Reasoning tokens are billed as output.
 */
data class TokenUsage(
    val input: Long,
    val output: Long,
    val reasoning: Long,
    val cacheRead: Long,
    val cacheWrite: Long
)
