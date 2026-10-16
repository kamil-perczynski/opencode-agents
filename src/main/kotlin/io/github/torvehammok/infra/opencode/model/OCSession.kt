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
