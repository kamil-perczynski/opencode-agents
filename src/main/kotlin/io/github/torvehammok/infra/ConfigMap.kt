package io.github.torvehammok.infra

import io.github.ktor_batterypack.core.ktor.KtorProps

data class ConfigMap(
    val ktor: KtorProps = KtorProps(),
    val slack: SlackProps = SlackProps(),
    val opencode: OpencodeProps = OpencodeProps()
)

data class SlackProps(
    val botToken: String = "",
    val appToken: String = "",
    val channels: List<SlackChannelProps> = emptyList()
)

data class SlackChannelProps(val id: String)

data class OpencodeProps(
    val attach: Boolean = true,
    val baseUrl: String = "http://localhost:4096",
    val username: String? = null,
    val password: String? = null,
    val model: String = "opencode/big-pickle",
    val connectTimeoutMs: Long = 500,
    val readTimeoutMs: Long = 2000,
    val agent : String = "slack-bot",
    val prompt: String = """
        ### TOOL GUIDELINES
        * **MCP**: You have authenticated access to github MCP
        
        ### RESPONSE GUIDELINES
        * Your response must be wrapped in <response> and </response> XML tags.
            
    """.trimIndent(),
    val maxAgentSessionDurationSeconds : Long = 200
)
