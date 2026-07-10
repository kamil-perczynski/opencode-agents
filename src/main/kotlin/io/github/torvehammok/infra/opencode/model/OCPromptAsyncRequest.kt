package io.github.torvehammok.infra.opencode.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class OCPromptAsyncRequest(
    val agent: String,
    val parts: List<OCPromptPart>,
    val model: OCPromptModel
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class OCPromptPart(
    val type: String,
    val text: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class OCPromptModel(
    val providerID: String,
    val modelID: String
)
