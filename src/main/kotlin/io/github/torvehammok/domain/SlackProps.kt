package io.github.torvehammok.domain

import com.fasterxml.jackson.annotation.JsonPropertyDescription
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty

data class SlackProps(
    @JsonPropertyDescription("Slack Bot User OAuth token")
    @NotBlank
    val botToken: String = "",
    @JsonPropertyDescription("Slack App-Level token for Socket Mode")
    @NotBlank
    val appToken: String = "",
    @JsonPropertyDescription("List of Slack channels to listen on")
    @NotEmpty
    val channels: List<SlackChannelProps> = emptyList(),
    @JsonPropertyDescription("Slack events server configuration")
    val server: SlackServerProps = SlackServerProps(),
    @JsonPropertyDescription("Slack signing secret for request verification")
    @NotBlank
    val signingSecret: String = ""
)