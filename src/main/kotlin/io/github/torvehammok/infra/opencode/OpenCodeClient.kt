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
import kotlinx.coroutines.CancellationException
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
     * acknowledges the subscription - events published before that moment (or while disconnected) are not delivered.
     * [onHeartbeat] is invoked for server heartbeats (SSE comments), which prove the connection is alive.
     *
     * Returns when the server closes the stream - it does not mean the session has finished.
     */
    suspend fun subscribeSessionEvents(
        sessionId: String,
        onConnected: () -> Unit = {},
        onHeartbeat: () -> Unit = {},
        onEvent: (event: OCEvent) -> Unit
    ) = withClientErrors("subscribe to opencode events") {
        httpClient.sse(
            urlString = "/api/event",
            request = {
                pathPattern("/api/event")
                opencodeAuth(opencodeProps)
                header(HttpHeaders.Accept, ContentType.Text.EventStream)
            },
            showCommentEvents = true
        ) {
            incoming.cancellable().collect { serverEvent ->
                currentCoroutineContext().ensureActive()

                val data = serverEvent.data
                if (data.isNullOrBlank()) {
                    if (serverEvent.comments != null) {
                        onHeartbeat()
                    }
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

    suspend fun createSession(agent: String, modelId: String): String = withClientErrors("open new session") {
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

        body.data.id
    }

    /**
     * @return id of the user message created by the prompt, null if the server did not return it
     */
    suspend fun prompt(sessionId: String, prompt: String): String? = withClientErrors("prompt session") {
        val response = httpClient.post {
            url("/api/session/$sessionId/prompt")
            pathPattern("/api/session/{sessionId}/prompt")
            opencodeAuth(opencodeProps)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(jsonMapper.writeValueAsString(OCPromptRequest(text = prompt)))
        }

        if (response.status == HttpStatusCode.NotFound) {
            throw OpenCodeSessionNotFoundException(sessionId)
        }

        if (!response.status.isSuccess()) {
            throw OpenCodeClientException("Failed to prompt session")
        }

        response.body<OCPromptResponse>().data?.id
    }

    suspend fun interrupt(sessionId: String) = withClientErrors("interrupt session") {
        val response = httpClient.post {
            url("/api/session/$sessionId/interrupt")
            pathPattern("/api/session/{sessionId}/interrupt")
            opencodeAuth(opencodeProps)
        }

        if (!response.status.isSuccess()) {
            throw OpenCodeClientException("Failed to interrupt session")
        }
    }

    /**
     * Ids of sessions with an execution currently running on the server - absent sessions are idle.
     */
    suspend fun fetchActiveSessionIds(): Set<String> = withClientErrors("fetch active sessions") {
        val response = httpClient.get {
            url("/api/session/active")
            pathPattern("/api/session/active")
            opencodeAuth(opencodeProps)
        }

        if (!response.status.isSuccess()) {
            throw OpenCodeClientException("Failed to fetch active sessions")
        }

        response.body<OCData<Map<String, Any?>>>().data.keys
    }

    /**
     * Session messages, newest first.
     */
    suspend fun fetchMessages(sessionId: String): List<OCSessionMessage> = withClientErrors("fetch session messages") {
        val response = httpClient.get {
            url("/api/session/$sessionId/message")
            pathPattern("/api/session/{sessionId}/message")
            opencodeAuth(opencodeProps)
        }

        if (!response.status.isSuccess()) {
            throw OpenCodeClientException("Failed to fetch session messages")
        }

        response.body<OCData<List<OCSessionMessage>>>().data
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

/**
 * Transport failures (timeouts, refused connections, broken streams) surface as [OpenCodeClientException],
 * so callers handle every opencode failure in one place.
 */
private suspend fun <T> withClientErrors(action: String, block: suspend () -> T): T {
    try {
        return block()
    } catch (ex: OpenCodeClientException) {
        throw ex
    } catch (ex: CancellationException) {
        throw ex
    } catch (ex: Exception) {
        throw OpenCodeClientException("Failed to $action: ${ex.message}", ex)
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
