package io.github.torvehammok.service

import com.slack.api.bolt.context.builtin.EventContext
import com.slack.api.model.block.Blocks
import com.slack.api.model.block.LayoutBlock
import io.github.torvehammok.io.github.torvehammok.domain.LoadingMessages
import io.github.torvehammok.io.github.torvehammok.infra.opencode.OCThread
import io.github.torvehammok.io.github.torvehammok.infra.opencode.OCThreadMessage
import io.github.torvehammok.io.github.torvehammok.infra.opencode.dto.OcAgentResponse
import org.koin.core.annotation.Singleton

@Singleton
class SlackThreadService {

    fun readThread(ctx: EventContext, threadTs: String, channel: String): OCThread {
        val threadResponse = ctx.client().conversationsReplies {
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

    fun postResponse(ctx: EventContext, channel: String?, threadTs: String?, ocResponse: OcAgentResponse) {
        val text = ocResponse.response
        val blocks = toBlocks(text, ocResponse)

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

    fun setThinkingStatus(ctx: EventContext, threadTs: String?, eventTs: String?, channel: String) {
        ctx.client().reactionsAdd {
            it.channel(channel)
                .timestamp(eventTs)
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

}

private fun toBlocks(text: String, ocResponse: OcAgentResponse): List<LayoutBlock> {
    val blocks = Blocks.asBlocks(
        Blocks.markdown { it.text(text) },
        Blocks.divider(),
        Blocks.markdown { it.text("""
            __Response generated in *${ocResponse.duration.inWholeSeconds}s__
            __It took ${ocResponse.toolsInvocations} tool invocations, roughly $${ocResponse.cost.toPlainString()}__
            _I am just a bot, I can make mistakes._
        """.trimIndent()) }
    )

    return blocks
}
