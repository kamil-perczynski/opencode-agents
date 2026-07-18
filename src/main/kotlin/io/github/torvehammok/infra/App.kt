package io.github.torvehammok.infra

import io.github.ktor_batterypack.core.KtorBatterypackCoreModule
import io.github.ktor_batterypack.core.ktor.KtorProps
import io.github.ktor_batterypack.metrics.KtorBatterypackMetricsModule
import io.github.ktor_batterypack.redis.KtorBatterypackRedisModule
import io.github.ktor_batterypack.redis.KtorBatterypackRedisStreamsModule
import io.github.ktor_batterypack.redis.RedisProps
import io.github.torvehammok.domain.ConfigSyncProps
import io.github.torvehammok.domain.OpenCodeProps
import io.github.torvehammok.infra.httpclient.KtorHttpClientFactory
import io.github.torvehammok.libs.RedisLocks
import io.github.torvehammok.libs.JsonMapperFactory
import io.ktor.client.*
import io.lettuce.core.api.StatefulRedisConnection
import org.koin.core.annotation.*
import tools.jackson.databind.json.JsonMapper

@KoinApplication(
    modules = [
        KtorBatterypackCoreModule::class,
        KtorBatterypackMetricsModule::class,
        KtorBatterypackRedisModule::class,
        KtorBatterypackRedisStreamsModule::class,
        AppModule::class
    ]
)
object App

@Module(
    includes = [
        KtorBatterypackRedisModule::class,
        KtorBatterypackRedisStreamsModule::class,
        KtorBatterypackMetricsModule::class,
    ]
)
@ComponentScan("io.github.torvehammok")
@Configuration
class AppModule {

    @Singleton
    fun opencodeProps(@Provided configMap: ConfigMap): OpenCodeProps {
        return configMap.opencode
    }

    @Singleton
    fun redisProps(@Provided configMap: ConfigMap): RedisProps {
        return configMap.redis
    }

    @Singleton
    @Named("opencode")
    fun opencodeClient(@Provided httpClientFactory: KtorHttpClientFactory, opencodeProps: OpenCodeProps): HttpClient {
        return httpClientFactory.createHttpClient(
            baseUrl = opencodeProps.baseUrl,
            connectTimeoutMs = opencodeProps.connectTimeoutMs,
            readTimeoutMs = opencodeProps.readTimeoutMs
        )
    }

    @Singleton
    fun redisLocks(statefulRedisConnection: StatefulRedisConnection<String, String>): RedisLocks {
        return RedisLocks(statefulRedisConnection)
    }

    @Singleton
    fun ktorProps(@Provided configMap: ConfigMap): KtorProps {
        return configMap.ktor
    }

    @Singleton
    fun jsonMapper(): JsonMapper {
        return JsonMapperFactory.createJsonMapper()
    }

    @Singleton
    fun configSyncProps(@Provided configMap: ConfigMap): ConfigSyncProps {
        return configMap.opencode.configSync
    }

}
