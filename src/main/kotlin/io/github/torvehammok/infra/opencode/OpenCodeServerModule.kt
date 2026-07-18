package io.github.torvehammok.infra.opencode

import io.github.ktor_batterypack.core.di.InitCallback
import io.github.ktor_batterypack.core.health.ReadinessCheck
import io.github.torvehammok.domain.ConfigSyncProps
import io.github.torvehammok.domain.OpenCodeProps
import io.github.torvehammok.infra.git.SSHGitConfigFetcher
import io.github.torvehammok.infra.git.SSHTransportConfigCallbackFactory
import org.eclipse.jgit.api.TransportConfigCallback
import org.koin.core.annotation.Module
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Singleton

@Module
class OpenCodeServerModule {

    @Singleton(binds = [InitCallback::class])
    fun openCodeServerManager(
        @Provided opencodeProps: OpenCodeProps,
        @Provided sshGitConfigFetcher: SSHGitConfigFetcher
    ): OpenCodeServerManager {
        return OpenCodeServerManager(opencodeProps, sshGitConfigFetcher)
    }

    @Singleton(binds = [ReadinessCheck::class])
    fun readinessCheck(@Provided openCodeClient: OpenCodeClient): OpenCodeReadinessCheck {
        return OpenCodeReadinessCheck(openCodeClient)
    }

    @Singleton
    fun sshTransportConfigCallbacks(@Provided configSyncProps: ConfigSyncProps): TransportConfigCallback {
        val factory = SSHTransportConfigCallbackFactory(configSyncProps.ssh)
        return factory.createSSHTransportCallback()
    }

}