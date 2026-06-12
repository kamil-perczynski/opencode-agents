package io.github.torvehammok.infra.slack

import com.slack.api.bolt.socket_mode.SocketModeApp
import io.github.ktor_batterypack.core.di.InitCallback
import org.koin.core.annotation.Singleton
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(SlackServer::class.java)

@Singleton
class SlackServer(private val socketModeApp: SocketModeApp) : InitCallback, AutoCloseable {

    override fun onInit() {
        log.info("Starting Slack Socket Mode App...")
        socketModeApp.start()
    }

    override fun close() {
        log.info("Stopping Slack Socket Mode App...")
        socketModeApp.stop()

    }

}