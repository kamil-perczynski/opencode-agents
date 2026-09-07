package io.github.torvehammok.domain

import com.fasterxml.jackson.annotation.JsonPropertyDescription
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank

data class SlackServerProps(
    @JsonPropertyDescription("Enable Slack events HTTP server")
    val enabled: Boolean = false,
    @JsonPropertyDescription("Base URL of the Slack server")
    @NotBlank
    val baseUrl: String = "http://127.0.0.1:3000",
    @NotBlank
    @JsonPropertyDescription("Slack events endpoint path")
    val path: String = "/slack/events",
    @JsonPropertyDescription("Slack server port")
    @Min(1024)
    @Max(65043)
    val port: Int = 3000
)