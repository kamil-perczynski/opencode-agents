package io.github.torvehammok.infra.opencode

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import io.github.torvehammok.domain.MockOCServerApi
import io.github.torvehammok.domain.OpenCodeAgentsIT
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.ktor.plugin.koin

class OpenCodeModelPricingProviderTest : OpenCodeAgentsIT() {

    private val wiremock: WireMockServer = application.koin().get()
    private val mockOCServerApi: MockOCServerApi = application.koin().get()
    private val openCodeClient: OpenCodeClient = application.koin().get()

    // a fresh instance per test - the provider caches prices
    private val pricingProvider = OpenCodeModelPricingProvider(openCodeClient)

    @BeforeEach
    fun setUp() {
        wiremock.resetAll()
    }

    @Test
    fun testFindsBasePriceOfModel(): Unit = runBlocking {
        // given:
        mockOCServerApi.mockModels(responseDef = { res, _ -> res.withBody(MODELS) })

        // when:
        val pricing = pricingProvider.findPricing("opencode-go/gpt-6-luna")

        // then: the base tier, not the long context one
        assertThat(pricing?.input).isEqualByComparingTo("0.1")
        assertThat(pricing?.output).isEqualByComparingTo("0.5")
        assertThat(pricing?.cacheRead).isEqualByComparingTo("0.01")
        assertThat(pricing?.cacheWrite).isEqualByComparingTo("0.125")
    }

    @Test
    fun testMatchesProviderAndModel(): Unit = runBlocking {
        // given:
        mockOCServerApi.mockModels(responseDef = { res, _ -> res.withBody(MODELS) })

        // expect: same model id under another provider is a different price
        assertThat(pricingProvider.findPricing("opencode-go/deepseek-v4.1-flash")?.input).isEqualByComparingTo("0.15")
        assertThat(pricingProvider.findPricing("openrouter/deepseek-v4.1-flash")?.input).isEqualByComparingTo("0.2")
    }

    @Test
    fun testUnknownModelHasNoPrice(): Unit = runBlocking {
        // given:
        mockOCServerApi.mockModels(responseDef = { res, _ -> res.withBody(MODELS) })

        // expect:
        assertThat(pricingProvider.findPricing("opencode-go/unknown")).isNull()
        assertThat(pricingProvider.findPricing("no-provider")).isNull()
    }

    @Test
    fun testServerErrorHasNoPrice(): Unit = runBlocking {
        // given:
        mockOCServerApi.mockModels(responseDef = { res, _ -> res.withStatus(500) })

        // expect:
        assertThat(pricingProvider.findPricing("opencode-go/deepseek-v4.1-flash")).isNull()
    }

    @Test
    fun testPricesAreCached(): Unit = runBlocking {
        // given:
        mockOCServerApi.mockModels(responseDef = { res, _ -> res.withBody(MODELS) })

        // when:
        pricingProvider.findPricing("opencode-go/deepseek-v4.1-flash")
        pricingProvider.findPricing("opencode-go/deepseek-v4.1-flash")

        // then:
        wiremock.verify(1, getRequestedFor(urlPathEqualTo("/api/model")))
    }
}

// shape of opencode 2.2 GET /api/model
private val MODELS = """
    {"location":{"directory":"/app"},"data":[
      {"id":"deepseek-v4.1-flash","modelID":"deepseek-v4.1-flash","providerID":"opencode-go","name":"DeepSeek V4.1 Flash",
       "cost":[{"input":0.15,"output":0.6,"cache":{"read":0.003,"write":0}}]},
      {"id":"deepseek-v4.1-flash","modelID":"deepseek-v4.1-flash","providerID":"openrouter","name":"DeepSeek V4.1 Flash",
       "cost":[{"input":0.2,"output":0.8,"cache":{"read":0.004,"write":0}}]},
      {"id":"gpt-6-luna","modelID":"gpt-6-luna","providerID":"opencode-go","name":"GPT-6 Luna",
       "cost":[{"input":0.1,"output":0.5,"cache":{"read":0.01,"write":0.125}},
               {"tier":{"type":"context","size":272000},"input":0.2,"output":0.75,"cache":{"read":0.02,"write":0.25}}]}
    ]}
""".trimIndent()
