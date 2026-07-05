package io.github.torvehammok.infra.opencode

import io.github.ktor_batterypack.core.di.InitCallback
import io.github.ktor_batterypack.core.health.ReadinessCheck
import io.github.torvehammok.domain.OpenCodeProps
import org.koin.core.annotation.Module
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Singleton

@Module
class OpenCodeServerModule {

    @Singleton(binds = [InitCallback::class])
    fun openCodeServerManager(@Provided opencodeProps: OpenCodeProps): OpenCodeServerManager {
        return OpenCodeServerManager(opencodeProps)
    }

    @Singleton(binds = [ReadinessCheck::class])
    fun readinessCheck(@Provided openCodeClient: OpenCodeClient): OpenCodeReadinessCheck {
        return OpenCodeReadinessCheck(openCodeClient)
    }

}