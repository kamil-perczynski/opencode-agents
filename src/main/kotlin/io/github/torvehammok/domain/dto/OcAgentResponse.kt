package io.github.torvehammok.domain.dto

import java.math.BigDecimal
import kotlin.time.Duration

data class OcAgentResponse(
    val response: String,
    val cost: BigDecimal,
    val toolsInvocations: Int,
    val duration: Duration
)