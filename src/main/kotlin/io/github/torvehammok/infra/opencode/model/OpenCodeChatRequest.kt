package io.github.torvehammok.infra.opencode.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class OpenCodeChatRequest(
    val agent: String? = null,
    val parts: List<Map<String, Any?>> = emptyList(),
    val model: Map<String, Any?> = emptyMap()
)
