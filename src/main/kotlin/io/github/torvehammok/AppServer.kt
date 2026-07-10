package io.github.torvehammok

import io.github.ktor_batterypack.core.config.loadConfig
import io.github.ktor_batterypack.core.configureKtorServer
import io.github.torvehammok.infra.App
import io.github.torvehammok.infra.ConfigMap
import io.github.torvehammok.infra.opencode.OpenCodeServerModule
import io.github.torvehammok.infra.slack.SlackModule
import io.ktor.server.application.*
import org.koin.dsl.module
import org.koin.plugin.module.dsl.module
import org.koin.plugin.module.dsl.withConfiguration
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("Application")

fun Application.configureServer() {
    configureKtorServer { profiles ->
        val configmap = loadConfig(profiles = profiles)
        modules(
            module {
                single { this@configureServer }
                single { configmap }
            }
        )

        if (configmap.opencode.server.enabled) {
            module<OpenCodeServerModule>()
        }

        if (configmap.slack.server.enabled) {
            module<SlackModule>()
        }

        withConfiguration<App>()
    }
}


private fun loadConfig(profiles: String): ConfigMap {
    val profileList = profiles.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    log.info("Loading application configuration with profiles: $profileList")
    return loadConfig(profileList, false)
}