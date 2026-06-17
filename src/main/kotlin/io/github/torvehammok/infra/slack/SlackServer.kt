package io.github.torvehammok.infra.slack

import com.slack.api.bolt.jetty.SlackAppServer
import io.github.ktor_batterypack.core.di.InitCallback
import kotlinx.coroutines.*
import org.koin.core.annotation.Singleton
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(SlackServer::class.java)

@Singleton
class SlackServer(private val socketModeApp: SlackAppServer) : InitCallback, AutoCloseable {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineName("SlackServer"))

    override fun onInit() {
        log.info("Starting Slack Socket Mode App...")
        scope.launch(Dispatchers.IO) {
            socketModeApp.start()
        }
    }

    override fun close() {
        log.info("Stopping Slack Socket Mode App...")
        socketModeApp.stop()

    }

}