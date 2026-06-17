package io.github.torvehammok.infra

import com.slack.api.bolt.AppConfig
import com.slack.api.bolt.jetty.SlackAppServer
import io.github.ktor_batterypack.core.KtorBatterypackCoreModule
import io.github.ktor_batterypack.core.config.loadConfig
import io.github.ktor_batterypack.core.ktor.KtorProps
import io.github.ktor_batterypack.metrics.KtorBatterypackMetricsModule
import io.github.ktor_batterypack.redis.KtorBatterypackRedisModule
import io.github.ktor_batterypack.redis.RedisProps
import io.github.torvehammok.infra.httpclient.KtorHttpClientFactory
import io.github.torvehammok.libs.JsonMapperFactory
import io.ktor.client.*
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.annotation.*
import org.slf4j.LoggerFactory
import tools.jackson.databind.json.JsonMapper
import com.slack.api.bolt.App as SlackApp

@KoinApplication(
    configurations = ["custom"],
    modules = [
        KtorBatterypackCoreModule::class,
        KtorBatterypackMetricsModule::class,
        KtorBatterypackRedisModule::class,
        AppModule::class
    ]
)
object App

private val log = LoggerFactory.getLogger(AppModule::class.java)

@Module
@ComponentScan("io.github.torvehammok")
@Configuration
class AppModule {

    @Singleton
    fun appConfig(@Property("app.profiles") profiles: String): ConfigMap {
        val profileList = profiles.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        log.info("Loading application configuration with profiles: $profileList")
        return loadConfig(profileList, false)
    }

    @Singleton
    fun opencodeProps(configMap: ConfigMap): OpencodeProps {
        return configMap.opencode
    }

    @Singleton
    fun redisProps(configMap: ConfigMap): RedisProps {
        return configMap.redis
    }

    @Singleton
    @Named("opencode")
    fun opencodeClient(httpClientFactory: KtorHttpClientFactory, opencodeProps: OpencodeProps): HttpClient {
        return httpClientFactory.createHttpClient(
            baseUrl = opencodeProps.baseUrl,
            connectTimeoutMs = opencodeProps.connectTimeoutMs,
            readTimeoutMs = opencodeProps.readTimeoutMs
        )
    }

    @Singleton
    @Named("slack")
    fun slackHttpClient(httpClientFactory: KtorHttpClientFactory, slackProps: SlackProps): HttpClient {
        return httpClientFactory.createHttpClient(
            baseUrl = slackProps.server.baseUrl,
            connectTimeoutMs = 1000,
            readTimeoutMs = 5000
        )
    }

    @Singleton
    fun ktorProps(configMap: ConfigMap): KtorProps {
        return configMap.ktor
    }

    @Singleton
    fun slackProps(configMap: ConfigMap): SlackProps {
        return configMap.slack
    }

    @Singleton
    fun jsonMapper(): JsonMapper {
        return JsonMapperFactory.createJsonMapper()
    }

    @Singleton
    fun coroutineScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineName("slack-processor"))
    }

    @Singleton
    fun slackApp(slackProps: SlackProps): SlackApp {
        val config = AppConfig.builder()
            .singleTeamBotToken(slackProps.botToken)
            .signingSecret(slackProps.signingSecret)
            .build()

        val slackApp = SlackApp(config)

        return slackApp
    }

    @Singleton
    fun slackSocketModeApp(slackProps: SlackProps, slackApp: SlackApp): SlackAppServer {
        return SlackAppServer(slackApp, slackProps.server.path, slackProps.server.port)
    }
}
