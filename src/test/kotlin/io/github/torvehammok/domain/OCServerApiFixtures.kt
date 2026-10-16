package io.github.torvehammok.domain

import io.github.torvehammok.infra.opencode.model.OCData
import io.github.torvehammok.infra.opencode.model.OCModelRef
import io.github.torvehammok.infra.opencode.model.OCSession
import kotlin.random.Random

fun randomAlphanumeric(random: Random, length: Int): String {
    val chars = ('a'..'z') + ('A'..'Z') + ('0'..'9')
    return (1..length).map { chars.random(random) }.joinToString("")
}

fun nextSessionId(): String {
    val random = Random.Default
    return "ses_" + randomAlphanumeric(random, 26)
}

fun someCreateSessionResponse(): OCData<OCSession> {
    return OCData(
        OCSession(
            id = "ses_0ae090e0dffeD2gZw8cFr4DA6K",
            agent = "slack-bot",
            model = OCModelRef(providerID = "opencode-go", id = "deepseek-v4.1-flash"),
            cost = 0.0,
            title = "New session - 2026-10-16T19:42:35.702Z"
        )
    )
}
