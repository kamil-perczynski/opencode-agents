package io.github.torvehammok.infra.opencode.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class OpenCodeSession(
    val id: String,
    val slug: String,
    val projectID: String,
    val directory: String,
    val path: String,
    val cost: Double,
    val tokens: Map<String, Any?> = emptyMap(),
    val title: String,
    val time: Map<String, Any?> = emptyMap()
)
