package io.github.torvehammok.domain

import com.fasterxml.jackson.annotation.JsonPropertyDescription

data class OpenCodeProps(
    @param:JsonPropertyDescription("Path to the opencode binary")
    val opencodeBinary: String = "opencode",
    @param:JsonPropertyDescription("OpenCode server configuration")
    val server: OpenCodeServerProps = OpenCodeServerProps(),
    @param:JsonPropertyDescription("Attach to terminal session")
    val attach: Boolean = true,
    @param:JsonPropertyDescription("OpenCode API base URL")
    val baseUrl: String = "http://127.0.0.1:12335",
    @param:JsonPropertyDescription("OpenCode API username")
    val username: String? = null,
    @param:JsonPropertyDescription("OpenCode API password")
    val password: String? = null,
    @param:JsonPropertyDescription("OpenCode model name")
    val model: String = "opencode/big-pickle",
    @param:JsonPropertyDescription("Connection timeout in milliseconds")
    val connectTimeoutMs: Long = 500,
    @param:JsonPropertyDescription("Read timeout in milliseconds")
    val readTimeoutMs: Long = 2000,
    @param:JsonPropertyDescription("OpenCode agent name")
    val agent: String = "slack-bot",
    @param:JsonPropertyDescription("System prompt for the agent")
    val prompt: String = """
        ### TOOL GUIDELINES
        * **MCP**: You have authenticated access to github MCP
        
        ### RESPONSE GUIDELINES
        * Your response must be wrapped in <response> and </response> XML tags.
            
    """.trimIndent(),
    @param:JsonPropertyDescription("Maximum agent session duration in seconds")
    val maxAgentSessionDurationSeconds: Long = 180,
    @param:JsonPropertyDescription("Configuration synchronization")
    val configSync: ConfigSyncProps = ConfigSyncProps()
)