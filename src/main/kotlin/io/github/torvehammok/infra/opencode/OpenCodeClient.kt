package io.github.torvehammok.infra.opencode

import io.github.ktor_batterypack.metrics.client.pathPattern
import io.github.torvehammok.domain.dto.OCMessage
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import org.koin.core.annotation.Named
import org.koin.core.annotation.Singleton
import tools.jackson.databind.JsonNode
import java.util.Base64

@Singleton
class OpenCodeClient(@Named("opencode") private val httpClient: HttpClient) {

    suspend fun fetchPendingSessions(): List<String> {
        val response = httpClient.get {
            pathPattern("/session/status")
            url("/session/status")
            headers {
                append("Authorization", javaBasicAuthHeader("opencode", "passwd"))
            }
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
            headers {
                append("Authorization", javaBasicAuthHeader("opencode", "passwd"))
            }
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


fun javaBasicAuthHeader(username: String, password: String): String {
    val credentials = "$username:$password"
    // Encode the credentials string into a Base64 string
    val encodedCredentials = Base64.getEncoder().encodeToString(credentials.toByteArray(Charsets.UTF_8))

    // Return the full header value
    return "Basic $encodedCredentials"
}
