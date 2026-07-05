package io.github.torvehammok.domain

data class SlackProps(
    val botToken: String = "",
    val appToken: String = "",
    val channels: List<SlackChannelProps> = emptyList(),
    val server: SlackServerProps = SlackServerProps(),
    val signingSecret: String = ""
)