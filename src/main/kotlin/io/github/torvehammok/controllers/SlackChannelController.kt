package io.github.torvehammok.controllers

import com.slack.api.bolt.App
import com.slack.api.model.event.MessageEvent
import io.github.ktor_batterypack.redis.RedisStreamPublisher
import io.github.torvehammok.domain.SlackProps
import io.github.torvehammok.infra.slack.SlackController
import io.github.torvehammok.infra.slack.SlackMessageListener.Companion.SLACK_MESSAGES_STREAM
import io.github.torvehammok.infra.slack.SlackRedisMessage
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(SlackChannelController::class.java)

class SlackChannelController(
    private val redisStreamPublisher: RedisStreamPublisher,
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
    }

}
