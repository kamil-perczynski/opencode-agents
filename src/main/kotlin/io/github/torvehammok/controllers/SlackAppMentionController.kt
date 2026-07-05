package io.github.torvehammok.controllers

import com.slack.api.bolt.App
import com.slack.api.model.event.AppMentionEvent
import com.slack.api.model.event.MessageChangedEvent
import com.slack.api.model.event.MessageDeletedEvent
import io.github.ktor_batterypack.redis.RedisStreamPublisher
import io.github.torvehammok.infra.slack.SlackController
import io.github.torvehammok.infra.slack.SlackMessageListener.Companion.SLACK_MESSAGES_STREAM
import io.github.torvehammok.infra.slack.SlackRedisMessage
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(SlackAppMentionController::class.java)

class SlackAppMentionController(
    private val redisStreamPublisher: RedisStreamPublisher,
) : SlackController {

    override fun register(app: App) {
        app.event(AppMentionEvent::class.java) { req, ctx ->
            val event = req.event
            log.info("Received an app mention event in channel {} from user {}", event.channel, event.user)

            redisStreamPublisher.publish(
                SLACK_MESSAGES_STREAM,
                SlackRedisMessage(
                    channelId = event.channel,
                    threadTs = event.threadTs,
                    ts = event.ts,
                    userId = event.user,
                    text = event.text ?: ""
                )
            )

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
