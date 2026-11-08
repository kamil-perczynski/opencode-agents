package io.github.torvehammok.infra.opencode.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class OCData<T>(
    val data: T
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class OCSession(
    val id: String,
    val agent: String? = null,
    val model: OCModelRef? = null,
    val cost: Double? = null,
    val title: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class OCModelRef(
    val providerID: String,
    val id: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class OCCreateSessionRequest(
    val agent: String? = null,
    val model: OCModelRef? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class OCPromptRequest(
    val text: String
)

/**
 * Response of `POST /api/session/{sessionID}/prompt` - the created user message.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class OCPromptResponse(
    val data: OCPromptMessage? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class OCPromptMessage(
    val id: String? = null
)

/**
 * Entry of `GET /api/session/{sessionID}/message`, e.g. `user`, `assistant` or `idle` (end of an execution).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class OCSessionMessage(
    val id: String? = null,
    val type: String? = null,
    val outcome: String? = null,
    val content: List<OCSessionMessageContent>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class OCSessionMessageContent(
    val type: String? = null,
    val text: String? = null
)
