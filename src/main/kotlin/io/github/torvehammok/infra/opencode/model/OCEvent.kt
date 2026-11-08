package io.github.torvehammok.infra.opencode.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * Event published on the opencode v2 global event bus (`GET /api/event`).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class OCEvent(
    val id: String? = null,
    val type: String? = null,
    val data: OCEventData? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class OCEventData(
    val sessionID: String? = null,
    val assistantMessageID: String? = null,
    val id: String? = null,
    val name: String? = null,
    val text: String? = null,
    val finish: String? = null,
    val cost: Double? = null,
    val error: OCStructuredError? = null,
    val reason: String? = null,
    val input: Map<String, Any?>? = null,
    val tokens: OCTokenUsage? = null
)

/**
 * Tokens of `session.usage.updated` - running totals of the whole session.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class OCTokenUsage(
    val input: Long = 0,
    val output: Long = 0,
    val reasoning: Long = 0,
    val cache: OCTokenCacheUsage? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class OCTokenCacheUsage(
    val read: Long = 0,
    val write: Long = 0
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class OCStructuredError(
    val type: String? = null,
    val message: String? = null
)
