package io.github.torvehammok.domain.dto

import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName

@JsonIgnoreProperties(ignoreUnknown = true)
data class OCMessage(
    @param:JsonProperty("info") val info: OCMessageInfo,
    @param:JsonProperty("parts") val parts: List<OCMessagePart> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class OCMessageInfo(
    @param:JsonProperty("id") val id: String? = null,
    @param:JsonProperty("sessionID") val sessionID: String? = null,
    @param:JsonProperty("role") val role: String? = null,
    @param:JsonProperty("time") val time: OCMessageTime? = null,
    @param:JsonProperty("summary") val summary: OCMessageSummary? = null,
    @param:JsonProperty("agent") val agent: String? = null,
    @param:JsonProperty("model") val model: OCMessageModel? = null,
    @param:JsonProperty("parentID") val parentID: String? = null,
    @param:JsonProperty("modelID") val modelID: String? = null,
    @param:JsonProperty("providerID") val providerID: String? = null,
    @param:JsonProperty("mode") val mode: String? = null,
    @param:JsonProperty("path") val path: OCMessagePath? = null,
    @param:JsonProperty("cost") val cost: Double? = null,
    @param:JsonProperty("tokens") val tokens: MessageTokenInfo? = null,
    @param:JsonProperty("finish") val finish: String? = null,
    @param:JsonAnySetter val unknownFields: Map<String, Any?> = mutableMapOf()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class OCMessageTime(
    @param:JsonProperty("created") val created: Long? = null,
    @param:JsonProperty("completed") val completed: Long? = null,
    @param:JsonAnySetter val unknownFields: Map<String, Any?> = mutableMapOf()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class OCMessageSummary(
    @param:JsonProperty("diffs") val diffs: List<Map<String, Any?>> = emptyList(),
    @param:JsonAnySetter val unknownFields: Map<String, Any?> = mutableMapOf()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class OCMessageModel(
    @param:JsonProperty("providerID") val providerID: String? = null,
    @param:JsonProperty("modelID") val modelID: String? = null,
    @param:JsonAnySetter val unknownFields: Map<String, Any?> = mutableMapOf()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class OCMessagePath(
    @param:JsonProperty("cwd") val cwd: String? = null,
    @param:JsonProperty("root") val root: String? = null,
    @param:JsonAnySetter val unknownFields: Map<String, Any?> = mutableMapOf()
)

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type",
    visible = true
)
@JsonSubTypes(
    JsonSubTypes.Type(MessageTextPart::class, name = "text"),
    JsonSubTypes.Type(MessageReasoningPart::class, name = "reasoning"),
    JsonSubTypes.Type(MessageStepStartPart::class, name = "step-start"),
    JsonSubTypes.Type(MessageStepFinishPart::class, name = "step-finish"),
    JsonSubTypes.Type(MessageToolPart::class, name = "tool")
)
@JsonIgnoreProperties(ignoreUnknown = true)
sealed class OCMessagePart

@JsonTypeName("text")
@JsonIgnoreProperties(ignoreUnknown = true)
data class MessageTextPart(
    @param:JsonProperty("id") val id: String? = null,
    @param:JsonProperty("sessionID") val sessionID: String? = null,
    @param:JsonProperty("messageID") val messageID: String? = null,
    @param:JsonProperty("type") val type: String? = null,
    @param:JsonProperty("text") val text: String? = null,
    @param:JsonProperty("time") val time: MessageTimeInfo? = null,
    @param:JsonAnySetter val unknownFields: Map<String, Any?> = mutableMapOf()
) : OCMessagePart()

@JsonTypeName("reasoning")
@JsonIgnoreProperties(ignoreUnknown = true)
data class MessageReasoningPart(
    @param:JsonProperty("id") val id: String? = null,
    @param:JsonProperty("sessionID") val sessionID: String? = null,
    @param:JsonProperty("messageID") val messageID: String? = null,
    @param:JsonProperty("type") val type: String? = null,
    @param:JsonProperty("text") val text: String? = null,
    @param:JsonProperty("time") val time: MessageTimeInfo? = null,
    @param:JsonAnySetter val unknownFields: Map<String, Any?> = mutableMapOf()
) : OCMessagePart()

@JsonTypeName("step-start")
@JsonIgnoreProperties(ignoreUnknown = true)
data class MessageStepStartPart(
    @param:JsonProperty("id") val id: String? = null,
    @param:JsonProperty("sessionID") val sessionID: String? = null,
    @param:JsonProperty("messageID") val messageID: String? = null,
    @param:JsonProperty("type") val type: String? = null,
    @param:JsonAnySetter val unknownFields: Map<String, Any?> = mutableMapOf()
) : OCMessagePart()

@JsonTypeName("step-finish")
@JsonIgnoreProperties(ignoreUnknown = true)
data class MessageStepFinishPart(
    @param:JsonProperty("id") val id: String? = null,
    @param:JsonProperty("sessionID") val sessionID: String? = null,
    @param:JsonProperty("messageID") val messageID: String? = null,
    @param:JsonProperty("type") val type: String? = null,
    @param:JsonProperty("reason") val reason: String? = null,
    @param:JsonProperty("snapshot") val snapshot: String? = null,
    @param:JsonProperty("cost") val cost: Double? = null,
    @param:JsonProperty("tokens") val tokens: MessageTokenInfo? = null,
    @param:JsonAnySetter val unknownFields: Map<String, Any?> = mutableMapOf()
) : OCMessagePart()

@JsonTypeName("tool")
@JsonIgnoreProperties(ignoreUnknown = true)
data class MessageToolPart(
    @param:JsonProperty("id") val id: String? = null,
    @param:JsonProperty("sessionID") val sessionID: String? = null,
    @param:JsonProperty("messageID") val messageID: String? = null,
    @param:JsonProperty("type") val type: String? = null,
    @param:JsonProperty("tool") val tool: String? = null,
    @param:JsonProperty("callID") val callID: String? = null,
    @param:JsonProperty("state") val state: MessageToolState? = null,
    @param:JsonAnySetter val unknownFields: Map<String, Any?> = mutableMapOf()
) : OCMessagePart()

@JsonIgnoreProperties(ignoreUnknown = true)
data class MessageToolState(
    @param:JsonProperty("status") val status: String? = null,
    @param:JsonProperty("input") val input: MessageToolInput? = null,
    @param:JsonProperty("output") val output: String? = null,
    @param:JsonProperty("title") val title: String? = null,
    @param:JsonProperty("metadata") val metadata: Map<String, Any?> = mutableMapOf(),
    @param:JsonProperty("time") val time: MessageTimeInfo? = null,
    @param:JsonAnySetter val unknownFields: Map<String, Any?> = mutableMapOf()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MessageToolInput(
    @param:JsonProperty("command") val command: String? = null,
    @param:JsonProperty("description") val description: String? = null,
    @param:JsonProperty("query") val query: String? = null,
    @param:JsonAnySetter val unknownFields: Map<String, Any?> = mutableMapOf()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MessageTimeInfo(
    @param:JsonProperty("start") val start: Long? = null,
    @param:JsonProperty("end") val end: Long? = null,
    @param:JsonAnySetter val unknownFields: Map<String, Any?> = mutableMapOf()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MessageTokenInfo(
    @param:JsonProperty("total") val total: Int? = null,
    @param:JsonProperty("input") val input: Int? = null,
    @param:JsonProperty("output") val output: Int? = null,
    @param:JsonProperty("reasoning") val reasoning: Int? = null,
    @param:JsonProperty("cache") val cache: MessageCacheInfo? = null,
    @param:JsonAnySetter val unknownFields: Map<String, Any?> = mutableMapOf()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MessageCacheInfo(
    @param:JsonProperty("read") val read: Int? = null,
    @param:JsonProperty("write") val write: Int? = null,
    @param:JsonAnySetter val unknownFields: Map<String, Any?> = mutableMapOf()
)
