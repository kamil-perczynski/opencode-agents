package io.github.torvehammok

import io.github.ktor_batterypack.core.configureKtorServer
import io.github.torvehammok.infra.App
import io.ktor.server.application.*
import org.koin.dsl.module
import org.koin.plugin.module.dsl.withConfiguration

fun Application.configureServer() {
    configureKtorServer { profiles ->
        modules(
            module {
                single { this@configureServer }
            }
        )
        withConfiguration<App>()
        properties(mapOf("app.profiles" to profiles))
    }
}
