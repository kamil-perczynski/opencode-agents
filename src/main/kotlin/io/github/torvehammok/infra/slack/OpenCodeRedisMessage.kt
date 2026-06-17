package io.github.torvehammok.infra.slack

import io.github.torvehammok.domain.dto.OCThread

data class OpenCodeRedisMessage(
    val sessionId: String,
    val text: String,
    val thread: OCThread
)