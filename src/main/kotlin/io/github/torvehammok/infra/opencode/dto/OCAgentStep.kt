package io.github.torvehammok.dto

import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName

@JsonIgnoreProperties(ignoreUnknown = true)
data class OCAgentStep(
    @param:JsonProperty("type") val type: String,
    @param:JsonProperty("timestamp") val timestamp: Long? = null,
    @param:JsonProperty("sessionID") val sessionID: String? = null,
    @param:JsonProperty("part") val part: OCAgentStepPart? = null,
    @param:JsonAnySetter val unknownFields: Map<String, Any?> = mutableMapOf()
)

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type",
    visible = true
)
@JsonSubTypes(
    JsonSubTypes.Type(StepStartPart::class, name = "step-start"),
    JsonSubTypes.Type(ToolPart::class, name = "tool"),
    JsonSubTypes.Type(StepFinishPart::class, name = "step-finish"),
    JsonSubTypes.Type(TextPart::class, name = "text")
)
@JsonIgnoreProperties(ignoreUnknown = true)
sealed class OCAgentStepPart

@JsonTypeName("step-start")
@JsonIgnoreProperties(ignoreUnknown = true)
data class StepStartPart(
    @param:JsonProperty("id") val id: String? = null,
    @param:JsonProperty("messageID") val messageID: String? = null,
    @param:JsonProperty("sessionID") val sessionID: String? = null,
    @param:JsonProperty("snapshot") val snapshot: String? = null,
    @param:JsonProperty("type") val type: String? = null,
    @param:JsonAnySetter val unknownFields: Map<String, Any?> = mutableMapOf()
) : OCAgentStepPart()

@JsonTypeName("tool")
@JsonIgnoreProperties(ignoreUnknown = true)
data class ToolPart(
    @param:JsonProperty("type") val type: String? = null,
    @param:JsonProperty("tool") val tool: String? = null,
    @param:JsonProperty("callID") val callID: String? = null,
    @param:JsonProperty("state") val state: ToolState? = null,
    @param:JsonProperty("id") val id: String? = null,
    @param:JsonProperty("sessionID") val sessionID: String? = null,
    @param:JsonProperty("messageID") val messageID: String? = null,
    @param:JsonAnySetter val unknownFields: Map<String, Any?> = mutableMapOf()
) : OCAgentStepPart()

@JsonIgnoreProperties(ignoreUnknown = true)
data class ToolState(
    @param:JsonProperty("status") val status: String? = null,
    @param:JsonProperty("input") val input: ToolInput? = null,
    @param:JsonProperty("output") val output: String? = null,
    @param:JsonProperty("metadata") val metadata: ToolMetadata? = null,
    @param:JsonProperty("title") val title: String? = null,
    @param:JsonProperty("time") val time: TimeInfo? = null,
    @param:JsonAnySetter val unknownFields: Map<String, Any?> = mutableMapOf()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ToolInput(
    @param:JsonProperty("command") val command: String? = null,
    @param:JsonProperty("description") val description: String? = null,
    @param:JsonAnySetter val unknownFields: Map<String, Any?> = mutableMapOf()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ToolMetadata(
    @param:JsonProperty("output") val output: String? = null,
    @param:JsonProperty("exit") val exit: Int? = null,
    @param:JsonProperty("description") val description: String? = null,
    @param:JsonProperty("truncated") val truncated: Boolean? = null,
    @param:JsonAnySetter val unknownFields: Map<String, Any?> = mutableMapOf()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TimeInfo(
    @param:JsonProperty("start") val start: Long? = null,
    @param:JsonProperty("end") val end: Long? = null,
    @param:JsonAnySetter val unknownFields: Map<String, Any?> = mutableMapOf()
)

@JsonTypeName("step-finish")
@JsonIgnoreProperties(ignoreUnknown = true)
data class StepFinishPart(
    @param:JsonProperty("id") val id: String? = null,
    @param:JsonProperty("reason") val reason: String? = null,
    @param:JsonProperty("snapshot") val snapshot: String? = null,
    @param:JsonProperty("messageID") val messageID: String? = null,
    @param:JsonProperty("sessionID") val sessionID: String? = null,
    @param:JsonProperty("type") val type: String? = null,
    @param:JsonProperty("tokens") val tokens: TokenInfo? = null,
    @param:JsonProperty("cost") val cost: Double? = null,
    @param:JsonAnySetter val unknownFields: Map<String, Any?> = mutableMapOf()
) : OCAgentStepPart()

@JsonIgnoreProperties(ignoreUnknown = true)
data class TokenInfo(
    @param:JsonProperty("total") val total: Int? = null,
    @param:JsonProperty("input") val input: Int? = null,
    @param:JsonProperty("output") val output: Int? = null,
    @param:JsonProperty("reasoning") val reasoning: Int? = null,
    @param:JsonProperty("cache") val cache: CacheInfo? = null,
    @param:JsonAnySetter val unknownFields: Map<String, Any?> = mutableMapOf()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CacheInfo(
    @param:JsonProperty("write") val write: Int? = null,
    @param:JsonProperty("read") val read: Int? = null,
    @param:JsonAnySetter val unknownFields: Map<String, Any?> = mutableMapOf()
)

@JsonTypeName("text")
@JsonIgnoreProperties(ignoreUnknown = true)
data class TextPart(
    @param:JsonProperty("id") val id: String? = null,
    @param:JsonProperty("messageID") val messageID: String? = null,
    @param:JsonProperty("sessionID") val sessionID: String? = null,
    @param:JsonProperty("type") val type: String? = null,
    @param:JsonProperty("text") val text: String? = null,
    @param:JsonProperty("time") val time: TimeInfo? = null,
    @param:JsonAnySetter val unknownFields: Map<String, Any?> = mutableMapOf()
) : OCAgentStepPart()
