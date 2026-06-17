package io.github.torvehammok.controllers

import io.github.ktor_batterypack.core.ktor.KtorController
import io.github.torvehammok.infra.SlackProps
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.core.annotation.Named
import org.koin.core.annotation.Singleton

@Singleton
class HttpSlackEventsController(
    private val slackProps: SlackProps,
    @Named("slack") private val internalSlackServerHttpClient: HttpClient
) : KtorController {
    override fun register(routing: Routing) {
        routing.post(slackProps.server.path) {
            val rawBodyBytes = call.receive<ByteArray>()

            val response = internalSlackServerHttpClient.post(slackProps.server.path) {
                setBody(rawBodyBytes)

                call.request.headers.forEach { key, values ->
                    val lowerKey = key.lowercase()
                    if (lowerKey != "host" && lowerKey != "content-length" && lowerKey != "transfer-encoding") {
                        values.forEach { value ->
                            headers.append(key, value)
                        }
                    }
                }
            }

            call.respondText(
                response.bodyAsText(),
                status = response.status,
                contentType = response.headers[HttpHeaders.ContentType]?.let { ContentType.parse(it) }
            )
        }
    }
}