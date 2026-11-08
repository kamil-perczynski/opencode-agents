package io.github.torvehammok.infra.opencode.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.math.BigDecimal

/**
 * Entry of `GET /api/model`.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class OCModelInfo(
    val id: String? = null,
    val modelID: String? = null,
    val providerID: String? = null,
    val cost: List<OCModelCost>? = null
)

/**
 * Price in USD per million tokens. Models may have several context tiers - the base one has no [tier].
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class OCModelCost(
    val tier: OCModelCostTier? = null,
    val input: BigDecimal? = null,
    val output: BigDecimal? = null,
    val cache: OCModelCacheCost? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class OCModelCostTier(
    val type: String? = null,
    val size: Long? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class OCModelCacheCost(
    val read: BigDecimal? = null,
    val write: BigDecimal? = null
)
