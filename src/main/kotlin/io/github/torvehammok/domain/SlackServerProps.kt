package io.github.torvehammok.domain

data class SlackServerProps(
    val enabled: Boolean = true,
    val baseUrl: String = "http://127.0.0.1:3000",
    val path: String = "/slack/events",
    val port: Int = 3000
)