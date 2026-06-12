package io.github.torvehammok.controllers

import com.slack.api.bolt.App
import com.slack.api.model.event.AppMentionEvent
import com.slack.api.model.event.MessageChangedEvent
import com.slack.api.model.event.MessageDeletedEvent
import io.github.torvehammok.domain.OcAgent
import io.github.torvehammok.infra.slack.SlackController
import io.github.torvehammok.domain.SlackThreadService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.annotation.Singleton
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(SlackAppMentionController::class.java)

@Singleton
class SlackAppMentionController(
    private val scope: CoroutineScope,
    private val threadService: SlackThreadService,
    private val ocAgent: OcAgent
) : SlackController {

    override fun register(app: App) {
        app.event(AppMentionEvent::class.java) { req, ctx ->
            val event = req.event
            log.info("Received an app mention event in channel {} from user {}", event.channel, event.user)

            val threadTs = event.threadTs ?: event.ts
            val channel = event.channel

            scope.launch {
                try {
                    threadService.setThinkingStatus(ctx, threadTs, event.ts, channel)
                    val ocThread = threadService.readThread(ctx, threadTs, channel)
                    val ocResponse = ocAgent.run(ocThread)
                    threadService.postResponse(ctx, channel, threadTs, ocResponse)
                } catch (e: Exception) {
                    log.error("Error processing app mention event", e)
                }
            }

            ctx.ack()
        }

        app.event(MessageDeletedEvent::class.java) { _, ctx ->
            ctx.ack()
        }

        app.event(MessageChangedEvent::class.java) { _, ctx ->
            ctx.ack()
        }

        app.command("/oc") { req, ctx ->
            log.info("Received /oc command from user {} in channel {}", req.payload.userId, req.payload.channelId)
            ctx.ack("Hello from /oc command! 👋")
        }
    }

}
