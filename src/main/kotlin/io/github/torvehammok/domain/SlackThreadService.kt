package io.github.torvehammok.domain

import com.slack.api.bolt.App
import com.slack.api.model.block.Blocks
import com.slack.api.model.block.LayoutBlock
import io.github.torvehammok.domain.dto.OCThread
import io.github.torvehammok.domain.dto.OCThreadMessage
import io.github.torvehammok.domain.dto.OcAgentResponse
import java.math.BigDecimal
import kotlin.time.Duration

class SlackThreadService(private val app: App) {

    fun readThread(threadTs: String, channel: String): OCThread {
        val client = app.client()

        val threadResponse = client.conversationsReplies {
            it.channel(channel).ts(threadTs)
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

    fun postResponse(channel: String?, threadTs: String?, ocResponse: OcAgentResponse) {
        val client = app.client()
        val text = ocResponse.response
        val blocks = toBlocks(text, ocResponse.duration, ocResponse.toolsInvocations, ocResponse.cost)

        client.chatPostMessage {
            it.channel(channel)
                .threadTs(threadTs)
                .text(text)
                .blocks(blocks)
        }

        client.reactionsAdd {
            it.channel(channel)
                .timestamp(threadTs)
                .name("checkered_flag")
        }
    }

    fun setThinkingStatus(threadTs: String?, eventTs: String?, channel: String) {
        val client = app.client()

        client.reactionsAdd {
            it.channel(channel)
                .timestamp(eventTs)
                .name("opencode")
        }

        client.assistantThreadsSetStatus {
            it.channelId(channel)
                .threadTs(threadTs)
                .status("is thinking...")
                .loadingMessages(
                    LoadingMessages.randomMessages()
                )
        }
    }

}

private fun toBlocks(
    text: String,
    duration: Duration,
    toolsInvocations: Int,
    cost: BigDecimal
): List<LayoutBlock> {
    val blocks = Blocks.asBlocks(
        Blocks.markdown { it.text(text) },
        Blocks.divider(),
        Blocks.markdown {
            it.text(
                """
                _Response generated in *${duration.inWholeSeconds}s*_
                _It took $toolsInvocations tool invocations, roughly *$${cost.toPlainString()}*_
                _I am just a bot, I can make mistakes._
                """.trimIndent()
            )
        }
    )

    return blocks
}
