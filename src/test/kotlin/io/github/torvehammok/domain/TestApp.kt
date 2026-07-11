package io.github.torvehammok.domain

import com.github.tomakehurst.wiremock.WireMockServer
import io.github.ktor_batterypack.core.KtorBatterypackCoreModule
import io.github.ktor_batterypack.metrics.KtorBatterypackMetricsModule
import io.github.ktor_batterypack.redis.KtorBatterypackRedisModule
import io.github.ktor_batterypack.redis.KtorBatterypackRedisStreamsModule
import io.github.torvehammok.infra.AppModule
import org.koin.core.annotation.*

@KoinApplication(
    modules = [
        TestModule::class,
        KtorBatterypackCoreModule::class,
        KtorBatterypackMetricsModule::class,
        KtorBatterypackRedisModule::class,
        KtorBatterypackRedisStreamsModule::class,
        AppModule::class
    ]
)
object TestApp

@Module(
    includes = [
        KtorBatterypackCoreModule::class,
        KtorBatterypackMetricsModule::class,
        KtorBatterypackRedisModule::class,
        KtorBatterypackRedisStreamsModule::class,
        AppModule::class
    ]
)
@Configuration
@ComponentScan("io.github.torvehammok")
class TestModule {

    @Singleton
    fun wiremock(): WireMockServer {
        return WireMockServer(18622)
    }

}

