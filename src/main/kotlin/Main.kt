package io.github.torvehammok

import com.slack.api.bolt.App
import com.slack.api.bolt.context.builtin.EventContext
import com.slack.api.bolt.socket_mode.SocketModeApp
import com.slack.api.model.block.Blocks
import com.slack.api.model.block.composition.MarkdownTextObject
import com.slack.api.model.event.*
import com.slack.api.model.view.View
import io.github.cdimascio.dotenv.dotenv
import io.github.torvehammok.io.github.torvehammok.OCThread
import io.github.torvehammok.io.github.torvehammok.OCThreadMessage
import io.github.torvehammok.io.github.torvehammok.OcAgentResponse
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

fun main() {
    dotenv {
        ignoreIfMissing = true
        systemProperties = true
    }

    val ocAgent = OcAgent(
        jsonMapper = JsonMapperFactory.createJsonMapper()
    )

    val botToken = System.getProperty("SLACK_BOT_TOKEN")
        ?: throw IllegalStateException("SLACK_BOT_TOKEN environment variable is missing")

    val config = com.slack.api.bolt.AppConfig.builder()
        .singleTeamBotToken(botToken)
        .build()

    val app = App(config)

    app.event(MessageDeletedEvent::class.java) { req, ctx ->
        ctx.ack()
    }

    val scope = CoroutineScope(CoroutineName("Boo"))

    app.event(AppMentionEvent::class.java) { req, ctx ->
        println("Received an app mention event in channel ${req.event.channel} from user ${req.event.user}")
        val event = req.event

        val threadTs = event.threadTs ?: event.ts
        val userId = event.user
        val channel = event.channel

        scope.launch {
            setAssistantThreadStatus(ctx, threadTs, req.event.channel)
            val ocThread = readThread(ctx, threadTs, event.channel)
            val ocResponse = ocAgent.run(ocThread)
            postMessage(ctx, channel, threadTs, ocResponse)
        }

        ctx.ack()
    }

    app.event(AppHomeOpenedEvent::class.java) { req, ctx ->
        println("Received an app home opened event from user ${req.event.user}")

        ctx.client().viewsPublish {
            it.userId(req.event.user)
                .view(
                    View.builder()
                        .type("home")
                        .build()
                )
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

        if (isNotBot(event) || isNotTopLevelMessage(event)) {
            return@event ctx.ack()
        }

        val threadTs = event.threadTs ?: event.ts
        val channel = event.channel

        scope.launch {
            setAssistantThreadStatus(ctx, threadTs, req.event.channel)
            val ocThread = readThread(ctx, threadTs, event.channel)
            val ocResponse = ocAgent.run(ocThread)
            postMessage(ctx, channel, threadTs, ocResponse)
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

private fun postMessage(
    ctx: EventContext,
    channel: String?,
    threadTs: String?,
    ocResponse: OcAgentResponse
) {
    val text = ocResponse.response

    val blocks = Blocks.asBlocks(
        Blocks.section {
            it.text(
                MarkdownTextObject.builder()
                    .text(text)
                    .build()
            )
        },
        Blocks.divider(),
        Blocks.context {
            it.elements(
                listOf(
                    MarkdownTextObject.builder()
                        .text(
                            """
                          _Response generated in *${ocResponse.duration.inWholeSeconds}s*_
                          _It took ${ocResponse.toolsInvocations} tool invocations, roughly $${ocResponse.cost.toPlainString()}_
                          _I am just a bot, I can make mistakes._
                        """.trimIndent()
                        )
                        .build(),
                )
            )
        }
    )

    ctx.client().chatPostMessage {
        it.channel(channel)
            .threadTs(threadTs)
            .text(text)
            .blocks(blocks)
    }


    ctx.client().reactionsAdd {
        it.channel(channel)
            .timestamp(threadTs)
            .name("checkered_flag")
    }
}

private fun isNotTopLevelMessage(event: MessageEvent): Boolean = event.threadTs != null

private fun isNotBot(event: MessageEvent): Boolean = event.botId != null

private fun readThread(
    ctx: EventContext,
    threadTs: String,
    channel: String
): OCThread {
    val threadResponse = ctx.client().conversationsReplies {
        it.channel(channel)
            .ts(threadTs)
    }

    return OCThread(
        channelId = channel,
        threadTs = threadTs,
        messages = threadResponse.messages
            ?.map { msg ->
                OCThreadMessage(
                    text = msg.text ?: "",
                    ts = msg.ts ?: "",
                    user = msg.user ?: "",
                    isBot = msg.botId != null
                )
            }
            ?: emptyList()
    )
}

private fun toBlocksJson(userId: String): String {
    return """
        [
            {
              "type": "section",
              "text": {
                "type": "mrkdwn",
                "text": "Hey <@$userId>, I received your mention in this channel!"
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
}

private suspend fun setAssistantThreadStatus(
    ctx: EventContext,
    threadTs: String?,
    channel: String
) {
    ctx.client().reactionsAdd {
        it.channel(channel)
            .timestamp(threadTs)
            .name("opencode")
    }

    ctx.client().assistantThreadsSetStatus {
        it.channelId(channel)
            .threadTs(threadTs)
            .status("is thinking...")
            .loadingMessages(
                LoadingMessages.randomMessages()
            )
    }
}