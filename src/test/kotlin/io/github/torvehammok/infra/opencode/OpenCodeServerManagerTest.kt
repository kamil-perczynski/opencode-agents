package io.github.torvehammok.infra.opencode

import com.github.tomakehurst.wiremock.WireMockServer
import io.github.ktor_batterypack.core.health.HealthStatus
import io.github.torvehammok.domain.ConfigSyncProps
import io.github.torvehammok.domain.MockOCServerApi
import io.github.torvehammok.domain.OpenCodeAgentsIT
import io.github.torvehammok.domain.OpenCodeProps
import io.github.torvehammok.domain.OpenCodeWatchdogProps
import io.github.torvehammok.infra.git.SSHGitConfigFetcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.jgit.api.TransportConfigCallback
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.ktor.plugin.koin
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class OpenCodeServerManagerTest : OpenCodeAgentsIT() {

    private val wiremock: WireMockServer = application.koin().get()
    private val mockOCServerApi: MockOCServerApi = application.koin().get()
    private val openCodeClient: OpenCodeClient = application.koin().get()
    private val opencodeProps: OpenCodeProps = application.koin().get()

    private val openCodeHealth = OpenCodeHealth()

    @BeforeEach
    fun setUp() {
        wiremock.resetAll()
        mockOCServerApi.mockHealthcheck()
    }

    @Test
    fun testRestartsServerOnStall(): Unit = runBlocking {
        // given:
        val manager = someManager(fakeOpencode("exec sleep 30"))

        try {
            manager.onInit()
            eventually { openCodeHealth.processState == OpenCodeProcessState.RUNNING }
            val firstPid = manager.pid

            // when:
            openCodeHealth.reportStall("ses_stalled")

            // then:
            eventually { manager.pid != firstPid && openCodeHealth.processState == OpenCodeProcessState.RUNNING }
            assertThat(ProcessHandle.of(firstPid!!).map { it.isAlive }.orElse(false)).isFalse

            // and: another stall right after the restart does not restart the server again
            val secondPid = manager.pid
            openCodeHealth.reportStall("ses_stalled_too")
            delay(500)
            assertThat(manager.pid).isEqualTo(secondPid)
        } finally {
            manager.close()
        }
    }

    @Test
    fun testRestartsServerOnUnexpectedExit(): Unit = runBlocking {
        // given: the server crashes on its first run only
        val marker = Files.createTempDirectory("opencode-test").resolve("started")
        val manager = someManager(
            fakeOpencode("""if [ -f "$marker" ]; then exec sleep 30; else touch "$marker"; exit 3; fi""")
        )

        try {
            // when:
            manager.onInit()

            // then:
            eventually {
                Files.exists(marker) &&
                    manager.pid?.let { pid -> ProcessHandle.of(pid).map { it.isAlive }.orElse(false) } == true &&
                    openCodeHealth.processState == OpenCodeProcessState.RUNNING
            }
        } finally {
            manager.close()
        }

        // and: closing stops the server for good
        assertThat(manager.pid).isNull()
    }

    @Test
    fun testReadinessIsDownWhileServerIsRestarting(): Unit = runBlocking {
        // given:
        val readinessCheck = OpenCodeReadinessCheck(openCodeClient, openCodeHealth)

        // expect:
        openCodeHealth.processState = OpenCodeProcessState.RESTARTING
        assertThat(readinessCheck.check().status).isEqualTo(HealthStatus.DOWN)

        openCodeHealth.processState = OpenCodeProcessState.STARTING
        assertThat(readinessCheck.check().status).isEqualTo(HealthStatus.DOWN)

        openCodeHealth.processState = OpenCodeProcessState.RUNNING
        assertThat(readinessCheck.check().status).isEqualTo(HealthStatus.UP)
    }

    private fun someManager(opencodeBinary: Path): OpenCodeServerManager {
        val props = opencodeProps.copy(
            opencodeBinary = opencodeBinary.toString(),
            configSync = ConfigSyncProps(enabled = false),
            watchdog = OpenCodeWatchdogProps(restartCooldownMs = 5_000, startupTimeoutMs = 3_000)
        )
        val gitConfigFetcher = SSHGitConfigFetcher(props.configSync, TransportConfigCallback { })

        return OpenCodeServerManager(props, gitConfigFetcher, openCodeHealth, openCodeClient)
    }

    private fun fakeOpencode(script: String): Path {
        val file = Files.createTempFile("opencode", ".sh")
        Files.writeString(file, "#!/bin/sh\n$script\n")
        file.toFile().setExecutable(true)
        return file
    }

    private suspend fun eventually(timeout: Duration = 10.seconds, condition: () -> Boolean) {
        withTimeout(timeout) {
            while (!condition()) {
                delay(50)
            }
        }
    }
}
