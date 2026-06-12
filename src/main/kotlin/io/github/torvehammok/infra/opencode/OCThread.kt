package io.github.torvehammok.infra.opencode

import com.sksamuel.hoplite.indent

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