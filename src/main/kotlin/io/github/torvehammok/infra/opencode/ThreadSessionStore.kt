@file:OptIn(ExperimentalLettuceCoroutinesApi::class)

package io.github.torvehammok.infra.opencode

import io.github.torvehammok.domain.OpenCodeProps
import io.github.torvehammok.domain.dto.ThreadSession
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.coroutines
import kotlinx.coroutines.flow.toList
import org.koin.core.annotation.Singleton
import java.math.BigDecimal

private const val SESSION_ID = "sessionId"
private const val LAST_TS = "lastTs"
private const val COST_TOTAL = "costTotal"

/**
 * Slack thread -> opencode session mapping, expiring after [OpenCodeProps.threadSessionTtlSeconds] of thread inactivity.
 */
@Singleton
class ThreadSessionStore(
    private val redisConnection: StatefulRedisConnection<String, String>,
    private val opencodeProps: OpenCodeProps
) {

    suspend fun find(channelId: String, threadTs: String): ThreadSession? {
        val fields = redisConnection.coroutines()
            .hgetall(toKey(channelId, threadTs))
            .toList()
            .associate { it.key to it.value }

        val sessionId = fields[SESSION_ID] ?: return null
        val lastTs = fields[LAST_TS] ?: return null

        return ThreadSession(
            sessionId = sessionId,
            lastTs = lastTs,
            costTotal = fields[COST_TOTAL]?.toBigDecimalOrNull() ?: BigDecimal.ZERO
        )
    }

    suspend fun save(channelId: String, threadTs: String, session: ThreadSession) {
        val key = toKey(channelId, threadTs)
        val redis = redisConnection.coroutines()

        redis.hset(
            key,
            mapOf(
                SESSION_ID to session.sessionId,
                LAST_TS to session.lastTs,
                COST_TOTAL to session.costTotal.toPlainString()
            )
        )
        redis.expire(key, opencodeProps.threadSessionTtlSeconds)
    }

    suspend fun delete(channelId: String, threadTs: String) {
        redisConnection.coroutines().del(toKey(channelId, threadTs))
    }

}

private fun toKey(channelId: String, threadTs: String): String = "opencode_thread_session:$channelId:$threadTs"
