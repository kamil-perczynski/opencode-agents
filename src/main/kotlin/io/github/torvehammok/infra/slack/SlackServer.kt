package io.github.torvehammok.infra.slack

import com.slack.api.bolt.jetty.SlackAppServer
import io.github.ktor_batterypack.core.di.InitCallback
import io.github.torvehammok.domain.SlackProps
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(SlackServer::class.java)

class SlackServer(
    private val slackAppServer: SlackAppServer,
    private val slackProps: SlackProps
) : InitCallback, AutoCloseable {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineName("SlackServer"))

    override fun onInit() {
        log.info("Starting Slack server at port {}...", slackProps.server.port)
        scope.launch(Dispatchers.IO) {
            slackAppServer.start()
        }
    }

    override fun close() {
        log.info("Stopping Slack server...")
        slackAppServer.stop()

    }

}