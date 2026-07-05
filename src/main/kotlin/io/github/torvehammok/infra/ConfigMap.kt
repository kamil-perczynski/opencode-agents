package io.github.torvehammok.infra

import io.github.ktor_batterypack.core.ktor.KtorProps
import io.github.ktor_batterypack.redis.RedisProps
import io.github.torvehammok.domain.OpenCodeProps
import io.github.torvehammok.domain.SlackProps

data class ConfigMap(
    val ktor: KtorProps = KtorProps(),
    val slack: SlackProps = SlackProps(),
    val opencode: OpenCodeProps = OpenCodeProps(),
    val redis: RedisProps = RedisProps()
)

