package io.github.torvehammok.infra.opencode

import io.github.ktor_batterypack.redis.RedisStreamListener
import io.github.torvehammok.domain.SlackThreadService
import io.github.torvehammok.domain.dto.OcAgentResponse
import io.github.torvehammok.libs.RedisLocks
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.coroutines
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import tools.jackson.databind.json.JsonMapper
import kotlin.time.Duration.Companion.seconds

private val log = LoggerFactory.getLogger(OpenCodeSessionCompletedListener::class.java)

@OptIn(ExperimentalLettuceCoroutinesApi::class)
class OpenCodeSessionCompletedListener(
    private val threadService: SlackThreadService,
    private val jsonMapper: JsonMapper,
    private val redisLocks: RedisLocks,
    private val statefulRedisConnection: StatefulRedisConnection<String, String>
) : RedisStreamListener {

    private val scope = CoroutineScope(Dispatchers.Default + CoroutineName("SlackMessageListener"))

    companion object {
        const val OPENCODE_SESSION_COMPLETED_STREAM = "opencode_session_completed"
    }

    override fun stream(): String = OPENCODE_SESSION_COMPLETED_STREAM

    override suspend fun onMessage(payload: String, headers: Map<String, String>) {
        log.info("Received redis stream message stream={}, payload={}, headers={}", stream(), payload, headers)

        scope.launch {
            val msg = jsonMapper.readValue(payload, OpenCodeSessionCompletedMessage::class.java)

            val data = statefulRedisConnection.coroutines()
                .hgetall("opencode_session:${msg.sessionId}")
                .toList()
                .associate { it.key to it.value }

            val threadTs = data["threadTs"]
            val channelId = data["channelId"]

            if (threadTs == null || channelId == null) {
                log.error("Missing threadTs or channelId for sessionId {}", msg.sessionId)
                return@launch
            }
            redisLocks.releaseLock(channelId, threadTs)
            threadService.postResponse(
                channel = channelId,
                threadTs = threadTs,
                ocResponse = OcAgentResponse(
                    response = msg.text,
                    duration = msg.durationSeconds.seconds,
                    cost = msg.cost,
                    toolsInvocations = msg.toolsInvocations
                )
            )
        }
    }
}