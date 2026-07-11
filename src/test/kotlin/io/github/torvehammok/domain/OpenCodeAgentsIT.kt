package io.github.torvehammok.domain

import io.github.ktor_batterypack.core.configureKtorServer
import io.github.ktor_batterypack.redis.testing.RedisTestContainer
import io.github.torvehammok.configureKoinAndKtor
import io.github.torvehammok.infra.App
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.jackson3.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import org.koin.plugin.module.dsl.withConfiguration
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.SerializationFeature
import tools.jackson.databind.cfg.DateTimeFeature
import tools.jackson.module.kotlin.KotlinModule

open class OpenCodeAgentsIT {

    companion object {
        private val redisContainer = RedisTestContainer()

        internal var application: Application
        internal var httpClient: HttpClient

        init {
            redisContainer.start()

            System.setProperty("config.override.redis.url", redisContainer.redisUri)

            val builder = ApplicationTestBuilder()
            builder.environment { config = MapApplicationConfig("app.profiles" to "test") }
            builder.application {
                val ktorApp = this

                configureKtorServer { profiles ->
                    val koinApp = this

                    properties(mapOf("app.profiles" to profiles))

                    configureKoinAndKtor(
                        profiles = profiles,
                        ktorApp = ktorApp,
                        koinApp = koinApp
                    )

                    withConfiguration<TestApp>()
                }
            }

            httpClient = builder.createClient {
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
                    level = LogLevel.ALL
                }
            }

            application = builder.application

            runBlocking {
                builder.startApplication()
            }
        }
    }
}
