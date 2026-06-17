package io.github.torvehammok.infra.slack

data class SlackRedisMessage(
    val channelId: String,
    val threadTs: String?,
    val ts: String,
    val userId: String,
    val text: String
)