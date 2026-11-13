package io.github.torvehammok.infra.slack

import io.github.ktor_batterypack.redis.RedisStreamListener
import io.github.ktor_batterypack.redis.RedisStreamPublisher
import io.github.torvehammok.domain.OpenCodeProps
import io.github.torvehammok.domain.SlackThreadService
import io.github.torvehammok.domain.lockTtlSeconds
import io.github.torvehammok.infra.opencode.OpenCodeMessageListener.Companion.OPENCODE_MESSAGES_STREAM
import io.github.torvehammok.infra.opencode.OpenCodeRedisMessage
import io.github.torvehammok.libs.RedisLocks
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.coroutines
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import tools.jackson.databind.json.JsonMapper
import java.util.*

private val log = LoggerFactory.getLogger(SlackMessageListener::class.java)

@OptIn(ExperimentalLettuceCoroutinesApi::class)
class SlackMessageListener(
    private val threadService: SlackThreadService,
    private val jsonMapper: JsonMapper,
    private val redisLocks: RedisLocks,
    private val redisStreamPublisher: RedisStreamPublisher,
    private val statefulRedisConnection: StatefulRedisConnection<String, String>,
    private val opencodeProps: OpenCodeProps
) : RedisStreamListener {

    private val scope = CoroutineScope(Dispatchers.Default + CoroutineName("SlackMessageListener"))

    companion object {
        const val SLACK_MESSAGES_STREAM = "slack_messages"
    }

    override fun stream(): String = SLACK_MESSAGES_STREAM

    override suspend fun onMessage(payload: String, headers: Map<String, String>) {
        log.info("Received redis stream message stream={}, payload={}, headers={}", stream(), payload, headers)
        scope.launch {

            val msg = jsonMapper.readValue(payload, SlackRedisMessage::class.java)

            val threadTs = msg.threadTs ?: msg.ts
            val channel = msg.channelId
            val lockOwner = UUID.randomUUID().toString()

            // the thread shares one opencode session - concurrent prompts would steer each other
            if (!redisLocks.tryLock(channel, threadTs, ttlSeconds = lockTtlSeconds(opencodeProps), owner = lockOwner)) {
                log.info("Thread {} in channel {} is already being processed. Skipping.", threadTs, channel)
                return@launch
            }

            try {
                val thread = threadService.readThread(threadTs = threadTs, channel = channel)

                threadService.setThinkingStatus(threadTs, msg.ts, channel)

                val sessionId = UUID.randomUUID().toString()
                val sessionKey = "opencode_session:${sessionId}"
                statefulRedisConnection.coroutines().hmset(
                    sessionKey,
                    mapOf("threadTs" to threadTs, "channelId" to channel, "lockOwner" to lockOwner),
                )
                statefulRedisConnection.coroutines().expire(sessionKey, lockTtlSeconds(opencodeProps) * 2)

                redisStreamPublisher.publish(
                    OPENCODE_MESSAGES_STREAM,
                    OpenCodeRedisMessage(
                        sessionId = sessionId,
                        text = msg.text,
                        thread = thread
                    )
                )
            } catch (ex: Exception) {
                try {
                    redisLocks.releaseLock(channel, threadTs, lockOwner)
                } catch (releaseEx: Exception) {
                    log.warn("Failed to release lock for thread {} in channel {}", threadTs, channel, releaseEx)
                }
                throw ex
            }
        }
    }
}

