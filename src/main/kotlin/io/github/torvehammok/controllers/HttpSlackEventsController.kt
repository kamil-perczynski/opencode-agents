package io.github.torvehammok.controllers

import io.github.ktor_batterypack.core.ktor.KtorController
import io.github.torvehammok.infra.SlackProps
import io.github.torvehammok.infra.httpclient.KtorHttpClientFactory
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
import org.koin.core.annotation.Singleton

@Singleton
class HttpSlackEventsController(
    private val httpClientFactory: KtorHttpClientFactory,
    private val slackProps: SlackProps
) : KtorController {
    override fun register(routing: Routing) {
        routing.post(slackProps.server.path) {
            val client = httpClientFactory.createHttpClient(
                baseUrl = slackProps.server.path,
                connectTimeoutMs = 1000,
                readTimeoutMs = 5000
            )
            val response = client.post(slackProps.server.path) {
                setBody(call.receiveText())
            }

            call.respondText(
                response.bodyAsText(),
                status = response.status,
                contentType = response.headers[HttpHeaders.ContentType]?.let { ContentType.parse(it) }
            )
        }
    }
}