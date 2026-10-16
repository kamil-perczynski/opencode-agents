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

private const val SERVER_CONNECTED = "server.connected"

/**
 * Client of the opencode v2 server API (https://opencode.ai/v2/docs/api).
 */
@Singleton
class OpenCodeClient(
    @Named("opencode") private val httpClient: HttpClient,
    private val opencodeProps: OpenCodeProps,
    private val jsonMapper: JsonMapper
) {

    /**
     * Subscribes to the global (volatile) event bus. [onConnected] is invoked once the server
     * acknowledges the subscription - events published before that moment are not delivered.
     */
    suspend fun subscribeSessionEvents(
        sessionId: String,
        onConnected: () -> Unit = {},
        onEvent: (event: OCEvent) -> Unit
    ) {
        httpClient.sse(
            urlString = "/api/event",
            request = {
                pathPattern("/api/event")
                opencodeAuth(opencodeProps)
                header(HttpHeaders.Accept, ContentType.Text.EventStream)
            },
        ) {
            incoming.cancellable().collect { serverEvent ->
                currentCoroutineContext().ensureActive()

                val data = serverEvent.data
                if (data.isNullOrBlank()) {
                    return@collect
                }

                val event = jsonMapper.readValue(data, OCEvent::class.java)

                if (event.type == SERVER_CONNECTED) {
                    onConnected()
                } else if (event.data?.sessionID == sessionId) {
                    onEvent(event)
                }
            }
        }
    }

    suspend fun createSession(agent: String, modelId: String): String {
        val response = httpClient.post {
            pathPattern("/api/session")
            url("/api/session")
            opencodeAuth(opencodeProps)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(
                jsonMapper.writeValueAsString(
                    OCCreateSessionRequest(agent = agent, model = toModelRef(modelId))
                )
            )
        }

        if (!response.status.isSuccess()) {
            throw OpenCodeClientException("Failed to open new session")
        }

        val body = response.body<OCData<OCSession>>()

        return body.data.id
    }

    suspend fun prompt(sessionId: String, prompt: String) {
        val response = httpClient.post {
            url("/api/session/$sessionId/prompt")
            pathPattern("/api/session/{sessionId}/prompt")
            opencodeAuth(opencodeProps)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(jsonMapper.writeValueAsString(OCPromptRequest(text = prompt)))
        }

        if (!response.status.isSuccess()) {
            throw OpenCodeClientException("Failed to prompt session")
        }
    }

    suspend fun interrupt(sessionId: String) {
        val response = httpClient.post {
            url("/api/session/$sessionId/interrupt")
            pathPattern("/api/session/{sessionId}/interrupt")
            opencodeAuth(opencodeProps)
        }

        if (!response.status.isSuccess()) {
            throw OpenCodeClientException("Failed to interrupt session")
        }
    }

    suspend fun fetchHealthcheckStatus(): HttpStatusCode {
        val response = httpClient.get {
            pathPattern("/api/info")
            url("/api/info")
            opencodeAuth(opencodeProps)
            attributes.put(DisableLogging, true)
        }

        return response.status
    }

}

private fun toModelRef(modelId: String): OCModelRef {
    val split = modelId.split("/", limit = 2)
    require(split.size == 2) { "Model id must be in format <provider>/<model>, got: $modelId" }

    return OCModelRef(providerID = split[0], id = split[1])
}

private fun HttpRequestBuilder.opencodeAuth(opencodeProps: OpenCodeProps) {
    headers {
        append("Authorization", javaBasicAuthHeader(opencodeProps.username, opencodeProps.password))
    }
}

fun javaBasicAuthHeader(username: String, password: String): String {
    val credentials = "$username:$password"
    // Encode the credentials string into a Base64 string
    val encodedCredentials = Base64.getEncoder().encodeToString(credentials.toByteArray(Charsets.UTF_8))

    // Return the full header value
    return "Basic $encodedCredentials"
}
