package io.github.torvehammok.infra

import com.fasterxml.jackson.annotation.JsonPropertyDescription
import io.github.ktor_batterypack.core.ktor.KtorProps
import io.github.ktor_batterypack.redis.RedisProps
import io.github.torvehammok.domain.OpenCodeProps
import io.github.torvehammok.domain.SlackProps

data class ConfigMap(
    @param:JsonPropertyDescription("Ktor server configuration")
    val ktor: KtorProps = KtorProps(),
    @param:JsonPropertyDescription("Slack integration configuration")
    val slack: SlackProps = SlackProps(),
    @param:JsonPropertyDescription("OpenCode agent configuration")
    val opencode: OpenCodeProps = OpenCodeProps(),
    @param:JsonPropertyDescription("Redis connection and stream configuration")
    val redis: RedisProps = RedisProps()
)

