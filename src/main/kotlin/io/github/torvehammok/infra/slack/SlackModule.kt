package io.github.torvehammok.infra.slack

import com.slack.api.bolt.AppConfig
import com.slack.api.bolt.jetty.SlackAppServer
import io.github.ktor_batterypack.core.di.InitCallback
import io.github.ktor_batterypack.core.ktor.KtorController
import io.github.ktor_batterypack.redis.KtorBatterypackRedisModule
import io.github.ktor_batterypack.redis.KtorBatterypackRedisStreamsModule
import io.github.ktor_batterypack.redis.RedisStreamListener
import io.github.ktor_batterypack.redis.RedisStreamPublisher
import io.github.torvehammok.controllers.HttpSlackEventsController
import io.github.torvehammok.controllers.SlackAppMentionController
import io.github.torvehammok.controllers.SlackChannelController
import io.github.torvehammok.domain.SlackThreadService
import io.github.torvehammok.infra.ConfigMap
import io.github.torvehammok.domain.SlackProps
import io.github.torvehammok.infra.httpclient.KtorHttpClientFactory
import io.github.torvehammok.infra.opencode.OpenCodeSessionCompletedListener
import io.github.torvehammok.libs.RedisLocks
import io.ktor.client.*
import io.lettuce.core.api.StatefulRedisConnection
import org.koin.core.annotation.Module
import org.koin.core.annotation.Named
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Singleton
import tools.jackson.databind.json.JsonMapper
import com.slack.api.bolt.App as SlackApp

@Module(includes = [KtorBatterypackRedisStreamsModule::class, KtorBatterypackRedisModule::class])
class SlackModule {

    @Singleton
    @Named("slack")
    fun slackHttpClient(@Provided httpClientFactory: KtorHttpClientFactory, slackProps: SlackProps): HttpClient {
        return httpClientFactory.createHttpClient(
            baseUrl = slackProps.server.baseUrl,
            connectTimeoutMs = 1000,
            readTimeoutMs = 5000
        )
    }

    @Singleton
    fun slackProps(@Provided configMap: ConfigMap): SlackProps {
        return configMap.slack
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
    fun slackAppServer(slackProps: SlackProps, slackApp: SlackApp): SlackAppServer {
        return SlackAppServer(slackApp, slackProps.server.path, slackProps.server.port)
    }

    @Singleton(binds = [InitCallback::class, AutoCloseable::class])
    fun slackServer(slackAppServer: SlackAppServer, slackProps: SlackProps): SlackServer {
        return SlackServer(slackAppServer, slackProps)
    }

    @Singleton(binds = [KtorController::class])
    fun httpSlackEventsController(
        slackProps: SlackProps,
        @Named("slack") internalSlackHttpClient: HttpClient
    ): HttpSlackEventsController {
        return HttpSlackEventsController(slackProps, internalSlackHttpClient)
    }

    @Singleton(binds = [SlackController::class])
    fun slackChannelController(
        slackProps: SlackProps,
        @Provided redisStreamPublisher: RedisStreamPublisher
    ): SlackChannelController {
        return SlackChannelController(redisStreamPublisher, slackProps)
    }

    @Singleton(binds = [SlackController::class])
    fun slackAppMentionController(@Provided redisStreamPublisher: RedisStreamPublisher): SlackAppMentionController {
        return SlackAppMentionController(redisStreamPublisher)
    }

    @Singleton(binds = [InitCallback::class])
    fun slackControllerRegistrar(app: SlackApp, controllers: List<SlackController>): SlackControllerRegistrar {
        return SlackControllerRegistrar(app, controllers)
    }

    @Singleton
    fun slackThreadService(app: SlackApp): SlackThreadService {
        return SlackThreadService(app)
    }

    @Singleton(binds = [RedisStreamListener::class])
    fun openCodeSessionCompletedListener(
        threadService: SlackThreadService,
        @Provided jsonMapper: JsonMapper,
        @Provided redisLocks: RedisLocks,
        statefulRedisConnection: StatefulRedisConnection<String, String>
    ): OpenCodeSessionCompletedListener {
        return OpenCodeSessionCompletedListener(threadService, jsonMapper, redisLocks, statefulRedisConnection)
    }

    @Singleton(binds = [RedisStreamListener::class])
    fun slackMessageListener(
        threadService: SlackThreadService,
        @Provided jsonMapper: JsonMapper,
        @Provided redisLocks: RedisLocks,
        redisStreamPublisher: RedisStreamPublisher,
        statefulRedisConnection: StatefulRedisConnection<String, String>
    ): SlackMessageListener {
        return SlackMessageListener(
            threadService,
            jsonMapper,
            redisLocks,
            redisStreamPublisher,
            statefulRedisConnection
        )
    }

}