package io.github.torvehammok.infra

import io.github.ktor_batterypack.core.ktor.KtorProps
import io.github.ktor_batterypack.redis.RedisProps

data class ConfigMap(
    val ktor: KtorProps = KtorProps(),
    val slack: SlackProps = SlackProps(),
    val opencode: OpencodeProps = OpencodeProps(),
    val redis: RedisProps = RedisProps()
)

data class SlackProps(
    val botToken: String = "",
    val appToken: String = "",
    val channels: List<SlackChannelProps> = emptyList(),
    val server : SlackServerProps = SlackServerProps()
)

data class SlackServerProps(
    val baseUrl : String = "http://127.0.0.1:3000",
    val path : String = "/slack/events",
    val port : Int = 3000
)

data class SlackChannelProps(val id: String)

data class OpencodeProps(
    val opencodeBinary : String = "opencode",
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
    val maxAgentSessionDurationSeconds : Long = 180
)
