package io.github.torvehammok.infra.opencode

import java.math.BigDecimal

data class OpenCodeSessionCompletedMessage(
    val text: String,
    val sessionId: String,
    val durationSeconds: Long,
    val cost: BigDecimal,
    val toolsInvocations: Int
)