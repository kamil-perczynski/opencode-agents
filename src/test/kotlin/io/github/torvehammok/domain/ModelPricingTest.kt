package io.github.torvehammok.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class ModelPricingTest {

    @Test
    fun testCostOfTokens() {
        // given: opencode-go/deepseek-v4.1-flash list price
        val pricing = ModelPricing(
            input = BigDecimal("0.15"),
            output = BigDecimal("0.6"),
            cacheRead = BigDecimal("0.003"),
            cacheWrite = BigDecimal.ZERO
        )

        // when:
        val cost = pricing.costOf(
            TokenUsage(input = 3648, output = 252, reasoning = 8, cacheRead = 779904, cacheWrite = 0)
        )

        // then: matches the cost opencode 2.0 computed for the same tokens
        assertThat(cost).isEqualByComparingTo("0.003042912")
    }

    @Test
    fun testCacheWritesArePriced() {
        // given:
        val pricing = ModelPricing(
            input = BigDecimal.ZERO,
            output = BigDecimal.ZERO,
            cacheRead = BigDecimal.ZERO,
            cacheWrite = BigDecimal("2")
        )

        // expect:
        assertThat(pricing.costOf(TokenUsage(input = 0, output = 0, reasoning = 0, cacheRead = 0, cacheWrite = 500_000)))
            .isEqualByComparingTo("1")
    }
}
