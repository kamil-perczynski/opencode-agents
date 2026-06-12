package io.github.torvehammok.controllers

import com.slack.api.bolt.App
import com.slack.api.model.event.MessageEvent
import io.github.torvehammok.io.github.torvehammok.domain.OcAgent
import io.github.torvehammok.infra.SlackProps
import io.github.torvehammok.infra.slack.SlackController
import io.github.torvehammok.service.SlackThreadService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.annotation.Singleton
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(SlackChannelController::class.java)

@Singleton
class SlackChannelController(
    private val scope: CoroutineScope,
    private val threadService: SlackThreadService,
    private val ocAgent: OcAgent,
    private val slackProps: SlackProps
) : SlackController {

    override fun register(app: App) {
        app.event(MessageEvent::class.java) { req, ctx ->
            val event = req.event
            log.info("Received a message event in channel {} from user {}", event.channel, event.user)

            if (event.botId != null || event.threadTs != null) {
                return@event ctx.ack()
            }

            val channelIds = slackProps.channels.map { it.id }.toSet()

            if (!channelIds.contains(event.channel)) {
                return@event ctx.ack()
            }

            val threadTs = event.threadTs ?: event.ts
            val channel = event.channel

            scope.launch {
                try {
                    threadService.setThinkingStatus(ctx, threadTs, event.ts, channel)
                    val ocThread = threadService.readThread(ctx, threadTs, channel)
                    val ocResponse = ocAgent.run(ocThread)
                    threadService.postResponse(ctx, channel, threadTs, ocResponse)
                } catch (e: Exception) {
                    log.error("Error processing channel message event", e)
                }
            }

            ctx.ack()
        }
    }

}
