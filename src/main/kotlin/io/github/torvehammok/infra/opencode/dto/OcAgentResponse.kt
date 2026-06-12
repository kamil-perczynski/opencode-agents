package io.github.torvehammok.io.github.torvehammok.infra.opencode.dto

import java.math.BigDecimal
import kotlin.time.Duration

class OcAgentResponse(val response: String, val cost: BigDecimal, val toolsInvocations: Int, val duration: Duration)