package io.github.torvehammok.infra.opencode

import io.github.ktor_batterypack.metrics.client.pathPattern
import io.github.torvehammok.domain.OpenCodeProps
import io.github.torvehammok.infra.httpclient.DisableLogging
import io.github.torvehammok.infra.opencode.model.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.cancellable
import org.koin.core.annotation.Named
import org.koin.core.annotation.Singleton
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
            urlString = "/global/event",
            request = { header(HttpHeaders.ContentType, ContentType.Text.EventStream) },
        ) {
            incoming.cancellable().collect { serverEvent ->
                currentCoroutineContext().ensureActive()

                val eventJson = jsonMapper.readValue(serverEvent.data, OCSseEvent::class.java)
                val eventSessionId = eventJson.payload?.properties?.sessionID

                if (eventSessionId == sessionId) {
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
            throw OpenCodeClientException("Failed to open new session")
        }

        val body = response.body<OpenCodeSession>()

        return body.id
    }

    suspend fun promptAsync(agent: String, prompt: String, sessionId: String, modelId: String) {
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
            throw OpenCodeClientException("Failed to prompt session")
        }
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
