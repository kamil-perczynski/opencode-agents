package io.github.torvehammok.infra.slack

import io.github.ktor_batterypack.redis.RedisStreamListener
import io.github.ktor_batterypack.redis.RedisStreamPublisher
import io.github.torvehammok.domain.SlackThreadService
import io.github.torvehammok.infra.slack.OpenCodeMessageListener.Companion.OPENCODE_MESSAGES_STREAM
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.coroutines
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.annotation.Singleton
import org.slf4j.LoggerFactory
import tools.jackson.databind.json.JsonMapper
import java.util.*

private val log = LoggerFactory.getLogger(SlackMessageListener::class.java)

@OptIn(ExperimentalLettuceCoroutinesApi::class)
@Singleton
class SlackMessageListener(
    private val threadService: SlackThreadService,
    private val jsonMapper: JsonMapper,
    private val redisLocks: RedisLocks,
    private val redisStreamPublisher: RedisStreamPublisher,
    private val statefulRedisConnection: StatefulRedisConnection<String, String>
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

            if (redisLocks.isLocked(channel, threadTs)) {
                log.info("Thread {} in channel {} is already being processed. Skipping.", threadTs, channel)
                return@launch
            }

            val thread = threadService.readThread(threadTs = threadTs, channel = channel)

            redisLocks.setLock(channel, threadTs, ttlSeconds = 180)
            threadService.setThinkingStatus(threadTs, msg.ts, channel)

            val sessionId = UUID.randomUUID().toString()
            statefulRedisConnection.coroutines().hmset(
                "opencode_session:${sessionId}",
                mapOf("threadTs" to threadTs, "channelId" to channel),
            )

            redisStreamPublisher.publish(
                OPENCODE_MESSAGES_STREAM,
                OpenCodeRedisMessage(
                    sessionId = sessionId,
                    text = msg.text,
                    thread = thread
                )
            )
        }
    }
}

