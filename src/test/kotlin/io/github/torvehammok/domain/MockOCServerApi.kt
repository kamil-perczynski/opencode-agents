package io.github.torvehammok.domain

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.status
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import com.github.tomakehurst.wiremock.matching.UrlPattern
import org.koin.core.annotation.Singleton
import tools.jackson.databind.json.JsonMapper

@Singleton
class MockOCServerApi(private val wiremock: WireMockServer, private val jsonMapper: JsonMapper) {

    fun mockCreateSession(
        mappingDef: MappingCustomizer = MAPPING_IDENTITY,
        responseDef: ResponseCustomizer = RESPONSE_DEF_IDENTITY
    ) {
        wiremock.stubFor(
            mappingDef.customize(
                mapping = post(urlPathEqualTo("/session"))
                    .willReturn(
                        responseDef.customize(
                            responseDef = status(200).withHeader("Content-Type", "application/json"),
                            jsonMapper = jsonMapper
                        )
                    ),
                jsonMapper = jsonMapper
            )
        )
    }

    fun mockSessionSSE(
        mappingDef: MappingCustomizer = MAPPING_IDENTITY,
        responseDef: ResponseCustomizer = RESPONSE_DEF_IDENTITY
    ) {
        wiremock.stubFor(
            mappingDef.customize(
                mapping = get(urlPathEqualTo("/global/event"))
                    .willReturn(
                        responseDef.customize(
                            responseDef = status(200).withHeader("Content-Type", "text/event-stream"),
                            jsonMapper = jsonMapper
                        )
                    ),
                jsonMapper = jsonMapper
            )
        )
    }

    fun mockPromptAsync(
        sessionId: String,
        mappingDef: MappingCustomizer = MAPPING_IDENTITY,
        responseDef: ResponseCustomizer = RESPONSE_DEF_IDENTITY
    ) {
        wiremock.stubFor(
            mappingDef.customize(
                mapping = post(urlPathEqualTo("/session/${sessionId}/prompt_async"))
                    .willReturn(
                        responseDef.customize(
                            responseDef = status(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody("{}"),
                            jsonMapper = jsonMapper
                        )
                    ),
                jsonMapper = jsonMapper
            )
        )
    }

    fun mockHealthcheck(
        mappingDef: MappingCustomizer = MAPPING_IDENTITY,
        responseDef: ResponseCustomizer = RESPONSE_DEF_IDENTITY
    ) {
        wiremock.stubFor(
            mappingDef.customize(
                mapping = get(urlPathEqualTo("/global/health"))
                    .willReturn(
                        responseDef.customize(
                            responseDef = status(200).withHeader("Content-Type", "application/json"),
                            jsonMapper = jsonMapper
                        )
                    ),
                jsonMapper = jsonMapper
            )
        )
    }

    internal inline fun <reified T> findRequests(urlPattern: UrlPattern): List<T> {
        return wiremock
            .findRequestsMatching(
                RequestPatternBuilder.newRequestPattern()
                    .withUrl(urlPattern)
                    .build()
            )
            .requests
            .map { jsonMapper.readValue(it.bodyAsString, T::class.java) }
    }

}

fun interface MappingCustomizer {
    fun customize(mapping: MappingBuilder, jsonMapper: JsonMapper): MappingBuilder
}

fun interface ResponseCustomizer {
    fun customize(responseDef: ResponseDefinitionBuilder, jsonMapper: JsonMapper): ResponseDefinitionBuilder
}

private val MAPPING_IDENTITY: MappingCustomizer = MappingCustomizer { mapping, _ -> mapping }
private val RESPONSE_DEF_IDENTITY: ResponseCustomizer = ResponseCustomizer { res, _ -> res }