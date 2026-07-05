package io.github.torvehammok.infra.httpclient

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.jackson3.*
import io.github.ktor_batterypack.metrics.client.ClientMicrometerMetricsPlugin
import io.ktor.util.AttributeKey
import io.micrometer.core.instrument.MeterRegistry
import org.koin.core.annotation.Singleton
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.SerializationFeature
import tools.jackson.databind.cfg.DateTimeFeature
import tools.jackson.module.kotlin.KotlinModule

@Singleton
class KtorHttpClientFactory(private val meterRegistry: MeterRegistry) {

    fun createHttpClient(
        baseUrl: String,
        connectTimeoutMs: Long,
        readTimeoutMs: Long
    ): HttpClient = HttpClient(CIO) {
        expectSuccess = false

        install(ContentNegotiation) {
            jackson {
                addModule(KotlinModule.Builder().build())
                enable(SerializationFeature.INDENT_OUTPUT)
                disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }

        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.HEADERS

            filter { request ->
                request.attributes.getOrNull(DisableLogging) != true
            }
        }

        install(HttpTimeout) {
            connectTimeoutMillis = connectTimeoutMs
            requestTimeoutMillis = readTimeoutMs
        }

        install(ClientMicrometerMetricsPlugin) {
            registry = meterRegistry
        }

        defaultRequest {
            url(baseUrl)
        }
    }
}

val DisableLogging = AttributeKey<Boolean>("Silent")