package io.github.torvehammok.domain

data class OpenCodeProps(
    val opencodeBinary: String = "opencode",
    val server: OpenCodeServerProps = OpenCodeServerProps(),
    val attach: Boolean = true,
    val baseUrl: String = "http://127.0.0.1:12335",
    val username: String? = null,
    val password: String? = null,
    val model: String = "opencode/big-pickle",
    val connectTimeoutMs: Long = 500,
    val readTimeoutMs: Long = 2000,
    val agent: String = "slack-bot",
    val prompt: String = """
        ### TOOL GUIDELINES
        * **MCP**: You have authenticated access to github MCP
        
        ### RESPONSE GUIDELINES
        * Your response must be wrapped in <response> and </response> XML tags.
            
    """.trimIndent(),
    val maxAgentSessionDurationSeconds: Long = 180,
    val configSync: ConfigSyncProps = ConfigSyncProps()
)