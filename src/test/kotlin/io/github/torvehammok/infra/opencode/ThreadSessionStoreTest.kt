@file:OptIn(ExperimentalLettuceCoroutinesApi::class)

package io.github.torvehammok.infra.opencode

import io.github.torvehammok.domain.OpenCodeAgentsIT
import io.github.torvehammok.domain.OpenCodeProps
import io.github.torvehammok.domain.dto.ThreadSession
import io.github.torvehammok.libs.RedisLocks
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.coroutines
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.koin.ktor.plugin.koin
import java.math.BigDecimal
import java.util.UUID

class ThreadSessionStoreTest : OpenCodeAgentsIT() {

    private val threadSessionStore: ThreadSessionStore = application.koin().get()
    private val redisLocks: RedisLocks = application.koin().get()
    private val redisConnection: StatefulRedisConnection<String, String> = application.koin().get()
    private val opencodeProps: OpenCodeProps = application.koin().get()

    @Test
    fun testSaveFindDelete(): Unit = runBlocking {
        // given:
        val channelId = "C${UUID.randomUUID()}"
        val session = ThreadSession(sessionId = "ses_1", lastTs = "100.000001", costTotal = BigDecimal("0.0125"))

        // when:
        threadSessionStore.save(channelId, "100.000001", session)

        // then:
        val found = threadSessionStore.find(channelId, "100.000001")
        assertThat(found?.sessionId).isEqualTo("ses_1")
        assertThat(found?.lastTs).isEqualTo("100.000001")
        assertThat(found?.costTotal).isEqualByComparingTo("0.0125")

        // and: the mapping expires when the thread goes idle
        val ttl = redisConnection.coroutines().ttl("opencode_thread_session:$channelId:100.000001")
        assertThat(ttl).isBetween(1, opencodeProps.threadSessionTtlSeconds)

        // when:
        threadSessionStore.delete(channelId, "100.000001")

        // then:
        assertThat(threadSessionStore.find(channelId, "100.000001")).isNull()
    }

    @Test
    fun testFindUnknownThread(): Unit = runBlocking {
        assertThat(threadSessionStore.find("C${UUID.randomUUID()}", "1.0")).isNull()
    }

    @Test
    fun testTryLockIsExclusive(): Unit = runBlocking {
        // given:
        val channelId = "C${UUID.randomUUID()}"

        // expect:
        assertThat(redisLocks.tryLock(channelId, "1.0", ttlSeconds = 60)).isTrue
        assertThat(redisLocks.tryLock(channelId, "1.0", ttlSeconds = 60)).isFalse

        redisLocks.releaseLock(channelId, "1.0")
        assertThat(redisLocks.tryLock(channelId, "1.0", ttlSeconds = 60)).isTrue
    }

    @Test
    fun testStaleOwnerCannotReleaseLock(): Unit = runBlocking {
        // given:
        val channelId = "C${UUID.randomUUID()}"

        // and:
        assertThat(redisLocks.tryLock(channelId, "1.0", ttlSeconds = 60, owner = "A")).isTrue

        // when: a stale owner tries to release
        redisLocks.releaseLock(channelId, "1.0", owner = "B")

        // then: the lock is still held
        assertThat(redisLocks.isLocked(channelId, "1.0")).isTrue

        // when: the real owner releases
        redisLocks.releaseLock(channelId, "1.0", owner = "A")

        // then: the lock is gone
        assertThat(redisLocks.isLocked(channelId, "1.0")).isFalse
    }
}
