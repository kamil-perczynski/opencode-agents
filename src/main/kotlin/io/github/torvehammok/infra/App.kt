package io.github.torvehammok.infra

import com.slack.api.bolt.App as SlackApp
import com.slack.api.bolt.AppConfig
import com.slack.api.bolt.socket_mode.SocketModeApp
import io.github.ktor_batterypack.core.KtorBatterypackCoreModule
import io.github.ktor_batterypack.core.config.loadConfig
import io.github.ktor_batterypack.core.ktor.KtorProps
import io.github.ktor_batterypack.metrics.KtorBatterypackMetricsModule
import io.github.torvehammok.io.github.torvehammok.libs.JsonMapperFactory
import io.github.torvehammok.httpclient.KtorHttpClientFactory
import io.github.torvehammok.infra.slack.SlackController
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.annotation.*
import tools.jackson.databind.json.JsonMapper

@KoinApplication(
    configurations = ["custom"],
    modules = [
        KtorBatterypackCoreModule::class,
        KtorBatterypackMetricsModule::class,
        AppModule::class
    ]
)
object App

private val log = org.slf4j.LoggerFactory.getLogger(AppModule::class.java)

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
    @Named("opencode")
    fun opencodeClient(httpClientFactory: KtorHttpClientFactory, opencodeProps: OpencodeProps): HttpClient {
        return httpClientFactory.createHttpClient(
            baseUrl = opencodeProps.baseUrl,
            connectTimeoutMs = opencodeProps.connectTimeoutMs,
            readTimeoutMs = opencodeProps.readTimeoutMs
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
    fun slackApp(slackProps: SlackProps, controllers: List<SlackController>): SlackApp {
        val config = AppConfig.builder()
            .singleTeamBotToken(slackProps.botToken)
            .build()

        val slackApp = SlackApp(config)

        for (controller in controllers) {
            log.info("Registering Slack controller: ${controller::class.java.simpleName}")
            controller.register(slackApp)
        }

        return slackApp
    }

    @Singleton
    fun slackSocketModeApp(slackProps: SlackProps, slackApp: SlackApp): SocketModeApp {
        return SocketModeApp(slackProps.appToken, slackApp)
    }
}
