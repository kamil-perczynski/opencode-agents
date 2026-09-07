package io.github.torvehammok.domain

import com.fasterxml.jackson.annotation.JsonPropertyDescription

data class SlackChannelProps(
    @param:JsonPropertyDescription("Slack channel ID")
    val id: String
)