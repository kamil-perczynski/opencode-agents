package io.github.torvehammok.infra.opencode

import io.github.ktor_batterypack.redis.RedisStreamListener
import io.github.ktor_batterypack.redis.RedisStreamPublisher
import io.github.torvehammok.domain.OcAgent
import io.github.torvehammok.libs.RedisLocks
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.annotation.Singleton
import org.slf4j.LoggerFactory
import tools.jackson.databind.json.JsonMapper

private val log = LoggerFactory.getLogger(OpenCodeMessageListener::class.java)

@Singleton
class OpenCodeMessageListener(
    private val redisStreamPublisher: RedisStreamPublisher,
    private val ocAgent: OcAgent,
    private val jsonMapper: JsonMapper,
    private val redisLocks: RedisLocks
) : RedisStreamListener {

    private val scope = CoroutineScope(Dispatchers.Default + CoroutineName("SlackMessageListener"))

    companion object {
        const val OPENCODE_MESSAGES_STREAM = "opencode_messages"
    }

    override fun stream(): String = OPENCODE_MESSAGES_STREAM

    override suspend fun onMessage(payload: String, headers: Map<String, String>) {
        log.info("Received redis stream message stream={}, payload={}, headers={}", stream(), payload, headers)

        scope.launch {
            val msg = jsonMapper.readValue(payload, OpenCodeRedisMessage::class.java)

            if (redisLocks.isLocked(msg.sessionId, "1")) {
                log.info("Channel {} is already being processed. Skipping.", msg.sessionId)
                return@launch
            }

            redisLocks.setLock(msg.sessionId, "1", ttlSeconds = 180)
            try {
                val ocResponse = ocAgent.run(msg.thread)

                redisStreamPublisher.publish(
                    OpenCodeSessionCompletedListener.OPENCODE_SESSION_COMPLETED_STREAM,
                    OpenCodeSessionCompletedMessage(
                        text = ocResponse.response,
                        sessionId = msg.sessionId,
                        durationSeconds = ocResponse.duration.inWholeSeconds,
                        toolsInvocations = ocResponse.toolsInvocations,
                        cost = ocResponse.cost
                    )
                )
            } finally {
                redisLocks.releaseLock(msg.sessionId, "1")
            }
        }
    }

}