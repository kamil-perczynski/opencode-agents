package io.github.torvehammok.infra.opencode

import io.github.ktor_batterypack.metrics.client.pathPattern
import io.github.torvehammok.domain.OpenCodeProps
import io.github.torvehammok.domain.dto.OCMessage
import io.github.torvehammok.infra.httpclient.DisableLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import org.koin.core.annotation.Named
import org.koin.core.annotation.Singleton
import tools.jackson.databind.JsonNode
import java.util.Base64

@Singleton
class OpenCodeClient(
    @Named("opencode") private val httpClient: HttpClient,
    private val opencodeProps: OpenCodeProps
) {

    suspend fun fetchHealthcheckStatus(): HttpStatusCode {
        val response = httpClient.get {
            pathPattern("/global/health")
            url("/global/health")
            opencodeAuth(opencodeProps)
            attributes.put(DisableLogging, true)
        }

        return response.status
    }

    suspend fun fetchPendingSessions(): List<String> {
        val response = httpClient.get {
            pathPattern("/session/status")
            url("/session/status")
            opencodeAuth(opencodeProps)
        }

        return when (response.status) {
            HttpStatusCode.OK ->
                response.body<List<String>>()

            HttpStatusCode.UnprocessableEntity -> {
                val problemDetail = response.body<JsonNode>()
                throw RuntimeException("Failed to fetch pending sessions: $problemDetail")
            }

            else ->
                throw RuntimeException("Unexpected error from opencode service: ${response.status}")
        }
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
