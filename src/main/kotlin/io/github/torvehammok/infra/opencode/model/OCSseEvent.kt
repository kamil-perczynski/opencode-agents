package io.github.torvehammok.infra.opencode.model

import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class OCSseEvent(
    val directory: String? = null,
    val project: String? = null,
    val payload: OCSsePayload? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class OCSsePayload(
    val id: String? = null,
    val type: String? = null,
    val properties: OCSseProperties? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class OCSseProperties(
    val sessionID: String? = null,
    val part: OCSsePart? = null,
    val info: OCSseInfo? = null,
    val status: OCSseStatus? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class OCSsePart(
    val id: String? = null,
    val messageID: String? = null,
    val sessionID: String? = null,
    val type: String? = null,
    val text: String? = null,
    val tool: String? = null,
    val callID: String? = null,
    val state: PartState? = null,
    val reason: String? = null,
    val snapshot: String? = null,
    val cost: Double? = null,
    val tokens: Map<String, Any?> = emptyMap(),
    val time: Map<String, Any?> = emptyMap()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PartState (
    val status: String? = null,
    @field:JsonAnySetter
    val other: MutableMap<String, Any> = mutableMapOf()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class OCSseInfo(
    val id: String? = null,
    val sessionID: String? = null,
    val parentID: String? = null,
    val role: String? = null,
    val mode: String? = null,
    val agent: String? = null,
    val finish: String? = null,
    val cost: Double? = null,
    val tokens: Map<String, Any?> = emptyMap(),
    val modelID: String? = null,
    val providerID: String? = null,
    val time: Map<String, Any?> = emptyMap()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class OCSseStatus(
    val type: String? = null
)
