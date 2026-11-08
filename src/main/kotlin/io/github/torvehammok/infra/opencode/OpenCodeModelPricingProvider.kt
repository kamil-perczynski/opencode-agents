package io.github.torvehammok.infra.opencode

import io.github.torvehammok.domain.ModelPricing
import io.github.torvehammok.domain.ModelPricingProvider
import io.github.torvehammok.infra.opencode.model.OCModelCost
import io.github.torvehammok.infra.opencode.model.OCModelInfo
import org.koin.core.annotation.Singleton
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap

private val log = LoggerFactory.getLogger(OpenCodeModelPricingProvider::class.java)

private const val CACHE_TTL_MS = 3_600_000L

/**
 * List prices from the opencode model catalog (`GET /api/model`).
 */
@Singleton(binds = [ModelPricingProvider::class])
class OpenCodeModelPricingProvider(private val openCodeClient: OpenCodeClient) : ModelPricingProvider {

    private val cache = ConcurrentHashMap<String, CachedPricing>()

    override suspend fun findPricing(modelId: String): ModelPricing? {
        val cached = cache[modelId]
        if (cached != null && System.currentTimeMillis() - cached.fetchedAt < CACHE_TTL_MS) {
            return cached.pricing
        }

        val models = try {
            openCodeClient.fetchModels()
        } catch (ex: OpenCodeClientException) {
            log.warn("Failed to fetch opencode model prices: {}", ex.message)
            return null
        }

        val pricing = findModel(models, modelId)?.let { toPricing(it) }
        if (pricing == null) {
            log.warn("No list price for model {}, session cost cannot be estimated", modelId)
            return null
        }

        cache[modelId] = CachedPricing(pricing, System.currentTimeMillis())
        return pricing
    }

}

private data class CachedPricing(val pricing: ModelPricing, val fetchedAt: Long)

private fun findModel(models: List<OCModelInfo>, modelId: String): OCModelInfo? {
    val split = modelId.split("/", limit = 2)
    if (split.size != 2) {
        return null
    }
    val (providerId, id) = split

    return models.firstOrNull { it.providerID == providerId && (it.modelID == id || it.id == id) }
}

/**
 * The base tier applies to the regular context size - the context size of each request is not known.
 */
private fun toPricing(model: OCModelInfo): ModelPricing? {
    val costs = model.cost ?: return null
    val base: OCModelCost = costs.firstOrNull { it.tier == null }
        ?: costs.minByOrNull { it.tier?.size ?: Long.MAX_VALUE }
        ?: return null

    return ModelPricing(
        input = base.input ?: BigDecimal.ZERO,
        output = base.output ?: BigDecimal.ZERO,
        cacheRead = base.cache?.read ?: BigDecimal.ZERO,
        cacheWrite = base.cache?.write ?: BigDecimal.ZERO
    )
}
