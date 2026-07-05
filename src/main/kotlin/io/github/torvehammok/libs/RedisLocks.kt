@file:OptIn(ExperimentalLettuceCoroutinesApi::class)

package io.github.torvehammok.libs

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.coroutines

class RedisLocks(private val redisConnection: StatefulRedisConnection<String, String>) {

    suspend fun setLock(channelId: String, ts: String, ttlSeconds: Long) {
        val key = toLockKey(channelId, ts)
        redisConnection.coroutines().setex(key, ttlSeconds, "1")
    }

    suspend fun releaseLock(channelId: String, ts: String) {
        val key = toLockKey(channelId, ts)
        redisConnection.coroutines().del(key)
    }

    suspend fun isLocked(channelId: String, ts: String): Boolean {
        val key = toLockKey(channelId, ts)
        val value = redisConnection.coroutines().get(key)
        return value != null
    }

}

private fun toLockKey(channelId: String, ts: String): String = "lock:$channelId:$ts"
