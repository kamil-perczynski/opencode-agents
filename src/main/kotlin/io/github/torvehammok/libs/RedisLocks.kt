@file:OptIn(ExperimentalLettuceCoroutinesApi::class)

package io.github.torvehammok.libs

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.SetArgs
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.coroutines
import java.util.UUID

class RedisLocks(private val redisConnection: StatefulRedisConnection<String, String>) {

    /**
     * Atomically acquires the lock. Returns false when it is already held.
     */
    suspend fun tryLock(channelId: String, ts: String, ttlSeconds: Long): Boolean {
        return tryLock(channelId, ts, ttlSeconds, UUID.randomUUID().toString())
    }

    /**
     * Atomically acquires the lock, storing [owner] as its value. Returns false when it is already held.
     */
    suspend fun tryLock(channelId: String, ts: String, ttlSeconds: Long, owner: String): Boolean {
        val key = toLockKey(channelId, ts)
        return redisConnection.coroutines().set(key, owner, SetArgs().nx().ex(ttlSeconds)) != null
    }

    suspend fun setLock(channelId: String, ts: String, ttlSeconds: Long) {
        val key = toLockKey(channelId, ts)
        redisConnection.coroutines().setex(key, ttlSeconds, "1")
    }

    suspend fun releaseLock(channelId: String, ts: String) {
        val key = toLockKey(channelId, ts)
        redisConnection.coroutines().del(key)
    }

    /**
     * Atomically releases the lock only if it is still owned by [owner].
     */
    suspend fun releaseLock(channelId: String, ts: String, owner: String) {
        val key = toLockKey(channelId, ts)
        redisConnection.coroutines().eval<Long>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            ScriptOutputType.INTEGER,
            arrayOf(key),
            owner,
        )
    }

    suspend fun isLocked(channelId: String, ts: String): Boolean {
        val key = toLockKey(channelId, ts)
        val value = redisConnection.coroutines().get(key)
        return value != null
    }

}

private fun toLockKey(channelId: String, ts: String): String = "lock:$channelId:$ts"
