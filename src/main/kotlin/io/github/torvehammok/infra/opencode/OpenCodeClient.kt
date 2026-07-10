package io.github.torvehammok.infra.opencode

import io.github.ktor_batterypack.metrics.client.pathPattern
import io.github.torvehammok.domain.OpenCodeProps
import io.github.torvehammok.domain.dto.OCMessage
import io.github.torvehammok.infra.httpclient.DisableLogging
import io.github.torvehammok.infra.opencode.model.OCPromptAsyncRequest
import io.github.torvehammok.infra.opencode.model.OCPromptModel
import io.github.torvehammok.infra.opencode.model.OCPromptPart
import io.github.torvehammok.infra.opencode.model.OCSseEvent
import io.github.torvehammok.infra.opencode.model.OpenCodeSession
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.request.*
import io.ktor.http.*
import org.koin.core.annotation.Named
import org.koin.core.annotation.Singleton
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.util.*


@Singleton
class OpenCodeClient(
    @Named("opencode") private val httpClient: HttpClient,
    private val opencodeProps: OpenCodeProps,
    private val jsonMapper: JsonMapper
) {

    suspend fun subscribeSessionEvents(sessionId: String, onEvent: (event: OCSseEvent) -> Unit) {
        httpClient.sse(
            "/global/event",
            request = { header(HttpHeaders.ContentType, ContentType.Text.EventStream) }
        ) {
            incoming.collect { serverEvent ->
                val eventJson = jsonMapper.readValue(serverEvent.data, OCSseEvent::class.java)

                if (eventJson.payload?.properties?.sessionID == sessionId) {
                    onEvent(eventJson)
                }
            }
        }
    }

    suspend fun createSession(): String {
        val response = httpClient.post {
            pathPattern("/session")
            url("/session")
            opencodeAuth(opencodeProps)
            setBody("{}")
            header(HttpHeaders.ContentType, ContentType.Application.Json)
        }

        if (!response.status.isSuccess()) {
            throw RuntimeException("Failed to open new session")
        }

        val body = response.body<OpenCodeSession>()

        return body.id
    }

    suspend fun promptAsync(agent: String, prompt: String, sessionId: String, modelId: String): HttpStatusCode {
        val split = modelId.split("/")
        val providerId = split[0]
        val model = split[1]

        val response = httpClient.post {
            url("/session/$sessionId/prompt_async")
            pathPattern("/session/{sessionId}/prompt_async")
            opencodeAuth(opencodeProps)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(
                jsonMapper.writeValueAsString(
                    OCPromptAsyncRequest(
                        agent = agent,
                        parts = listOf(OCPromptPart(type = "text", text = prompt)),
                        model = OCPromptModel(
                            providerID = providerId,
                            modelID = model
                        )
                    )
                )
            )
        }

        if (!response.status.isSuccess()) {
            throw RuntimeException("Failed to prompt session")
        }

        return response.status
    }

    suspend fun fetchHealthcheckStatus(): HttpStatusCode {
        val response = httpClient.get {
            pathPattern("/global/health")
            url("/global/health")
            opencodeAuth(opencodeProps)
            attributes.put(DisableLogging, true)
        }

        return response.status
    }

    suspend fun fetchSessionMessages(sessionId: String): List<OCMessage> {
        val response = httpClient.get {
            pathPattern("/session/{sessionId}/message")
            url("/session/$sessionId/message")
            opencodeAuth(opencodeProps)
        }

        return when (response.status) {
            HttpStatusCode.OK ->
                response.body<List<OCMessage>>()

            HttpStatusCode.UnprocessableEntity -> {
                val problemDetail = response.body<JsonNode>()
                throw RuntimeException("Failed to fetch session messages: $problemDetail")
            }

            else ->
                throw RuntimeException("Unexpected error from opencode service: ${response.status}")
        }
    }

}

private fun HttpRequestBuilder.opencodeAuth(opencodeProps: OpenCodeProps) {
    if (opencodeProps.password != null) {
        headers {
            append("Authorization", javaBasicAuthHeader(opencodeProps.username ?: "username", opencodeProps.password))
        }
    }
}

fun javaBasicAuthHeader(username: String, password: String): String {
    val credentials = "$username:$password"
    // Encode the credentials string into a Base64 string
    val encodedCredentials = Base64.getEncoder().encodeToString(credentials.toByteArray(Charsets.UTF_8))

    // Return the full header value
    return "Basic $encodedCredentials"
}
