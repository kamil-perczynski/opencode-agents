package io.github.torvehammok.domain.dto

import com.sksamuel.hoplite.indent
import java.math.BigDecimal

data class OCThread(
    val channelId: String,
    val threadTs: String,
    val messages: List<OCThreadMessage>
)

data class OCThreadMessage(
    val text: String,
    val ts: String,
    val user: String,
    val isBot: Boolean
)

/**
 * Messages not yet seen by a session that has already processed the thread up to [lastTs].
 * The newest human message is always included, so the message that triggered the run is never lost.
 */
fun messagesAfter(thread: OCThread, lastTs: String): List<OCThreadMessage> {
    val humanMessages = thread.messages.filter { !it.isBot }
    val last = lastTs.toBigDecimalOrNull()

    val newer = humanMessages.filter { msg ->
        val ts = msg.ts.toBigDecimalOrNull()
        last == null || ts == null || ts > last
    }

    return newer.ifEmpty { listOfNotNull(humanMessages.lastOrNull()) }
}

/**
 * Newest message ts of the thread, as known at the time it was read.
 */
fun latestTs(thread: OCThread): String? {
    return thread.messages.maxByOrNull { it.ts.toBigDecimalOrNull() ?: BigDecimal.ZERO }?.ts
}

fun toXml(thread: OCThread): String {
    val messagesXml = thread.messages.joinToString(separator = "\n") { msg ->
        """
        <message>
            <text>${escapeXml(msg.text)}</text>
            <ts>${escapeXml(msg.ts)}</ts>
            <user>${escapeXml(msg.user)}</user>
            <isBot>${msg.isBot}</isBot>
        </message>
        """.trimIndent()
    }
    return """
    <thread>
        <channelId>${escapeXml(thread.channelId)}</channelId>
        <threadTs>${escapeXml(thread.threadTs)}</threadTs>
        <messages>
            ${messagesXml.indent("        ")}
        </messages>
    </thread>
    """.trimIndent()
}

fun escapeXml(input: String): String {
    return input
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}