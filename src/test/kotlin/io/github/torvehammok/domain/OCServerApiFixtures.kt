package io.github.torvehammok.domain

import io.github.torvehammok.infra.opencode.model.OpenCodeSession
import kotlin.random.Random

fun randomAlphanumeric(random: Random, length: Int): String {
    val chars = ('a'..'z') + ('A'..'Z') + ('0'..'9')
    return (1..length).map { chars.random(random) }.joinToString("")
}

fun nextSessionId(): String {
    val random = Random.Default
    return "ses_" + randomAlphanumeric(random, 26)
}

fun someCreateSessionRequest(): OpenCodeSession {
    return OpenCodeSession(
        id = "ses_0ae090e0dffeD2gZw8cFr4DA6K",
        slug = "snazzy-merman",
        projectID = "cb1c117ac52a6eb4af57d0b810b330630d776e88",
        directory = "/app/opencode-agents",
        path = "",
        cost = 0.0,
        title = "New session - 2026-07-10T19:42:35.702Z"
    )
}