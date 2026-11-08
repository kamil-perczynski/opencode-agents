package io.github.torvehammok.dto

import io.github.torvehammok.domain.dto.OCThread
import io.github.torvehammok.domain.dto.OCThreadMessage
import io.github.torvehammok.domain.dto.latestTs
import io.github.torvehammok.domain.dto.messagesAfter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OCThreadTest {

    private val thread = OCThread(
        channelId = "C1",
        threadTs = "9.000001",
        messages = listOf(
            OCThreadMessage(text = "question", ts = "9.000001", user = "U1", isBot = false),
            OCThreadMessage(text = "answer", ts = "9.000002", user = "B1", isBot = true),
            OCThreadMessage(text = "comment", ts = "10.000001", user = "U2", isBot = false),
            OCThreadMessage(text = "follow-up", ts = "10.000010", user = "U1", isBot = false)
        )
    )

    @Test
    fun testMessagesAfterSkipsSeenAndBotMessages() {
        // expect: ts are compared as numbers, not strings ("10.x" > "9.x")
        assertThat(messagesAfter(thread, "9.000002").map { it.text }).containsExactly("comment", "follow-up")
    }

    @Test
    fun testMessagesAfterAlwaysIncludesNewestHumanMessage() {
        // expect: nothing new, e.g. the message was already processed
        assertThat(messagesAfter(thread, "10.000010").map { it.text }).containsExactly("follow-up")
    }

    @Test
    fun testLatestTs() {
        assertThat(latestTs(thread)).isEqualTo("10.000010")
    }
}
