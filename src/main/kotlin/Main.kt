package io.github.torvehammok

import com.slack.api.bolt.App
import com.slack.api.bolt.socket_mode.SocketModeApp
import com.slack.api.model.block.Blocks
import com.slack.api.model.block.Blocks.context
import com.slack.api.model.block.Blocks.divider
import com.slack.api.model.block.Blocks.section
import com.slack.api.model.block.composition.BlockCompositions.markdownText
import com.slack.api.model.block.element.BlockElements.asContextElements
import com.slack.api.model.event.AppMentionEvent
import com.slack.api.model.event.MessageChangedEvent
import com.slack.api.model.event.MessageDeletedEvent
import com.slack.api.model.event.MessageEvent
import io.github.cdimascio.dotenv.dotenv
import okhttp3.Dispatcher
import kotlin.text.Typography.section

fun main() {
    dotenv {
        ignoreIfMissing = true
        systemProperties = true
    }

    val botToken = System.getProperty("SLACK_BOT_TOKEN")
        ?: throw IllegalStateException("SLACK_BOT_TOKEN environment variable is missing")

    val config = com.slack.api.bolt.AppConfig.builder()
        .singleTeamBotToken(botToken)
        .build()

    val app = App(config)

    app.event(MessageDeletedEvent::class.java) { req, ctx ->
        ctx.ack()
    }


    app.event(AppMentionEvent::class.java) { req, ctx ->
        println("Received an app mention event in channel ${req.event.channel} from user ${req.event.user}")
        val event = req.event

        ctx.client().chatPostMessage {
            it.channel(event.channel)
                .threadTs(req.event.ts)
                .text("")
                .blocksAsString(
                    """
                    [
                        {
                          "type": "section",
                          "text": {
                            "type": "mrkdwn",
                            "text": "Hey <@${req.event.user}>, I received your mention in this channel!"
                          }
                        },
                        {
                          "type": "divider"
                        },
                        {
                          "type": "context",
                          "elements": [
                            {
                              "type": "mrkdwn",
                              "text": "_I am just a bot, I can make mistakes._"
                            }
                          ]
                        }
                      ]
                """.trimIndent()
                )
        }

        // Print the whole thread conversation (if mention is in a thread, fetch from parent)
        val threadTs = event.threadTs ?: event.ts
        val threadResponse = ctx.client().conversationsReplies {
            it.channel(event.channel)
                .ts(threadTs)
        }

        println("Thread content (channel=${event.channel}, thread_ts=${threadTs}):")
        threadResponse.messages?.forEach { msg ->
            println("  [user=${msg.user}] ${msg.text}")
        }

        ctx.ack()
    }

    app.event(MessageChangedEvent::class.java) { req, ctx ->
        ctx.ack()
    }

    // 2. Handler for messages in a specific channel
    app.event(MessageEvent::class.java) { req, ctx ->
        val event = req.event
        println("Received a message event in channel ${event.channel} from user ${event.user}")

        if (req.event.botId != null) {
            return@event ctx.ack()
        }

        app.executorService().submit {
            if (event.channelType == "im" && event.botId == null) {
                println(event.text)
                ctx.client().chatPostMessage {
                    it.channel(event.channel) // "Dxxxxxx" - The unique DM channel ID between user and bot
                        .threadTs(req.event.ts)
                        .text("Hey <@${event.user}>, I received your direct message!")
                }
            } else {
                // Filter: specific channel, plain messages only, ignore bot messages, ignore thread replies
                val subtype = event.subtype
                val channel = event.channel
                val botId = event.botId
                val user = event.user
                val threadTs = event.threadTs

                if (subtype == null
                    && channel == "C0B783F5CLW"
                    && botId == null
                    && user != ctx.botUserId
                    && (threadTs == null || threadTs == event.ts)
                ) {
                    try {
                        // 2a. Add a reaction to the incoming message
                        val reactionsAdd = ctx.client().reactionsAdd {
                            it.channel(event.channel)
                                .timestamp(event.ts)
                                .name("thinking_face")
                        }

                        val thinkingResponse = ctx.client().chatPostMessage {
                            it.channel(event.channel)
                                .text("Thinking... :hourglass_flowing_sand:")
                                .threadTs(event.ts)
                        }

                        // 2c. Wait 3 seconds
                        Thread.sleep(3000)

                        // 2d. Update the thinking message with the actual response
                        ctx.client().chatUpdate {
                            it.channel(event.channel)
                                .ts(thinkingResponse.ts)
                                .text("Here's my response after 3 seconds of deep thought! :bulb:")
                        }
                        ctx.client().reactionsAdd {
                            it.channel(event.channel)
                                .timestamp(event.ts)
                                .name("checkered_flag")
                        }

                        // 2e. Print the whole thread conversation (if message is in a thread, fetch from parent)
                        val threadTs = event.threadTs ?: event.ts
                        val threadResponse = ctx.client().conversationsReplies {
                            it.channel(event.channel)
                                .ts(threadTs)
                        }

                        println("Thread content (channel=${event.channel}, thread_ts=${threadTs}):")
                        threadResponse.messages?.forEach { msg ->
                            println("  [user=${msg.user}] ${msg.text}")
                        }
                    } catch (e: Exception) {
                        ctx.logger.error("Error processing message in channel ${event.channel}", e)
                    }
                }

                // Always ack to avoid the 3-second Slack timeout
            }
        }
        ctx.ack()
    }

    // 3. Handler for slack command "/oc"
    app.command("/oc") { req, ctx ->
        println("Received /oc command from user ${req.payload.userId} in channel ${req.payload.channelId}")
        println("Command text: ${req.payload.text}")
        ctx.ack("Hello from /oc command! 👋")
    }

    // 4. Grab your Slack App Token (starts with xapp-)
    val appToken = System.getProperty("SLACK_APP_TOKEN")
        ?: throw IllegalStateException("SLACK_APP_TOKEN environment variable is missing")

    // 5. Initialize and start the Jakarta-compatible SocketModeApp
    val socketModeApp = SocketModeApp(appToken, app)

    println("Starting Slack Bolt App in Socket Mode...")
    socketModeApp.start()
}