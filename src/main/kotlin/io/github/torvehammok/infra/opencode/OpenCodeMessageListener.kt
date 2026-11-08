package io.github.torvehammok.infra.opencode

import io.github.ktor_batterypack.redis.RedisStreamListener
import io.github.ktor_batterypack.redis.RedisStreamPublisher
import io.github.torvehammok.domain.OCServerAgent
import io.github.torvehammok.domain.dto.OcAgentResponse
import io.github.torvehammok.domain.dto.OcAgentRun
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.annotation.Singleton
import org.slf4j.LoggerFactory
import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal
import kotlin.time.Duration

private val log = LoggerFactory.getLogger(OpenCodeMessageListener::class.java)

private const val UNEXPECTED_FAILURE_RESPONSE = "Oops! Something went wrong on our side. Just try again later."

/**
 * Runs the agent for a Slack message. The Slack thread is locked by [io.github.torvehammok.infra.slack.SlackMessageListener]
 * until the completion message is handled, so runs of the same thread never overlap.
 */
@Singleton
class OpenCodeMessageListener(
    private val redisStreamPublisher: RedisStreamPublisher,
    private val serverAgent: OCServerAgent,
    private val jsonMapper: JsonMapper,
    private val threadSessionStore: ThreadSessionStore
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
            val thread = msg.thread

            // always reply - the completion message also releases the Slack thread lock
            val ocResponse = try {
                val previous = threadSessionStore.find(thread.channelId, thread.threadTs)
                val run = serverAgent.run(thread, previous)

                updateThreadSession(msg, run)
                run.response
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                log.error("Agent run for session {} has failed unexpectedly", msg.sessionId, ex)
                OcAgentResponse(
                    response = UNEXPECTED_FAILURE_RESPONSE,
                    cost = BigDecimal.ZERO,
                    toolsInvocations = 0,
                    duration = Duration.ZERO
                )
            }

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
        }
    }

    private suspend fun updateThreadSession(msg: OpenCodeRedisMessage, run: OcAgentRun) {
        val thread = msg.thread
        val session = run.session

        if (session != null) {
            threadSessionStore.save(thread.channelId, thread.threadTs, session)
        } else {
            log.info("Thread {} in channel {} will start a new session on the next message", thread.threadTs, thread.channelId)
            threadSessionStore.delete(thread.channelId, thread.threadTs)
        }
    }

}
