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
    val reason: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class OCStructuredError(
    val type: String? = null,
    val message: String? = null
)
