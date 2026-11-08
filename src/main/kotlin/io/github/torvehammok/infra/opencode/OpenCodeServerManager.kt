package io.github.torvehammok.infra.opencode

import io.github.ktor_batterypack.core.di.InitCallback
import io.github.torvehammok.domain.OpenCodeProps
import io.github.torvehammok.infra.git.SSHGitConfigFetcher
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val log = LoggerFactory.getLogger(OpenCodeServerManager::class.java)

private const val HEALTH_POLL_INTERVAL_MS = 500L

/**
 * Spawns `opencode serve` and keeps it running: restarts it when it exits unexpectedly or when a session stalls
 * (a wedged session executor is only cleared by a restart - see anomalyco/opencode#53747).
 */
class OpenCodeServerManager(
    private val opencodeProps: OpenCodeProps,
    private val sshGitConfigFetcher: SSHGitConfigFetcher,
    private val openCodeHealth: OpenCodeHealth,
    private val openCodeClient: OpenCodeClient
) : AutoCloseable, InitCallback {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineName("opencode-server"))

    private val watchdogProps get() = opencodeProps.watchdog

    private val restartMutex = Mutex()

    @Volatile
    private var process: Process? = null

    @Volatile
    private var closing = false

    @Volatile
    private var startedAt = 0L

    @Volatile
    private var lastRestartAt = 0L

    private var restartAttempt = 0

    internal val pid: Long?
        get() = process?.pid()

    override fun onInit() {
        if (opencodeProps.configSync.enabled) {
            sshGitConfigFetcher.gitClone()
        }
        start()

        scope.launch(CoroutineName("opencode-health")) {
            if (!awaitHealthy()) {
                restart("server did not become healthy within ${watchdogProps.startupTimeoutMs} ms", requested = false)
            }
        }
        scope.launch(CoroutineName("opencode-restarts")) {
            openCodeHealth.restartRequests.collect { request ->
                restart(request.reason, requested = true)
            }
        }
    }

    fun start(): Process {
        val command = listOf(
            opencodeProps.opencodeBinary,
            "serve",
            "--hostname", "0.0.0.0",
            "--port", opencodeProps.server.port.toString()
        )

        log.info("Starting OpenCode server: {}", command.joinToString(" "))
        openCodeHealth.processState = OpenCodeProcessState.STARTING

        val builder = ProcessBuilder(command)
            .redirectErrorStream(false)

        val env = builder.environment()

        opencodeProps.server.env.custom.forEach { (key, value) ->
            env[key] = value
        }

        val started = builder.start()
        process = started
        startedAt = System.currentTimeMillis()

        started.onExit().thenAccept { onProcessExit(it) }

        scope.launch(CoroutineName("opencode-stdout")) {
            logStream(started.inputStream, false)
        }
        scope.launch(CoroutineName("opencode-stderr")) {
            logStream(started.errorStream, true)
        }

        return started
    }

    private fun onProcessExit(exited: Process) {
        // processes stopped on purpose are detached first; a restart in progress handles its own failures
        if (closing || process !== exited || restartMutex.isLocked) {
            return
        }

        log.error("OpenCode server has exited unexpectedly with code {}", exited.exitValue())
        openCodeHealth.processState = OpenCodeProcessState.RESTARTING

        scope.launch {
            restart("server exited with code ${exited.exitValue()}", requested = false)
        }
    }

    /**
     * @param requested restart requested due to a stalled session - such requests within the cooldown are dropped,
     * as the server has just been restarted anyway
     */
    private suspend fun restart(reason: String, requested: Boolean) = restartMutex.withLock {
        if (closing) {
            return@withLock
        }

        val sinceLastRestart = System.currentTimeMillis() - lastRestartAt
        if (requested && sinceLastRestart < watchdogProps.restartCooldownMs) {
            log.warn("Skipping OpenCode server restart ({}), it was restarted {} ms ago", reason, sinceLastRestart)
            return@withLock
        }

        log.error("Restarting OpenCode server: {}", reason)
        openCodeHealth.processState = OpenCodeProcessState.RESTARTING
        stopProcess()

        if (System.currentTimeMillis() - startedAt > watchdogProps.backoffResetAfterMs) {
            restartAttempt = 0
        }

        while (!closing) {
            delay(restartBackoff(restartAttempt++))

            try {
                start()
            } catch (ex: Exception) {
                log.error("Failed to start OpenCode server: {}", ex.message, ex)
                continue
            }

            if (awaitHealthy()) {
                break
            }

            log.error("OpenCode server did not become healthy within {} ms, restarting again", watchdogProps.startupTimeoutMs)
            openCodeHealth.processState = OpenCodeProcessState.RESTARTING
            stopProcess()
        }

        lastRestartAt = System.currentTimeMillis()
    }

    private suspend fun awaitHealthy(): Boolean {
        val healthy = withTimeoutOrNull(watchdogProps.startupTimeoutMs.milliseconds) {
            while (!isServerHealthy()) {
                delay(HEALTH_POLL_INTERVAL_MS)
            }
            true
        } ?: false

        if (healthy && !closing) {
            log.info("OpenCode server is healthy")
            openCodeHealth.processState = OpenCodeProcessState.RUNNING
        }
        return healthy
    }

    private suspend fun isServerHealthy(): Boolean {
        if (process?.isAlive != true) {
            return false
        }

        return try {
            openCodeClient.fetchHealthcheckStatus().value in 200..<300
        } catch (ex: CancellationException) {
            throw ex
        } catch (_: Exception) {
            false
        }
    }

    private fun restartBackoff(attempt: Int): Long {
        if (attempt == 0) {
            return 0
        }
        val backoff = 1_000L shl (attempt - 1).coerceAtMost(20)
        return backoff.coerceAtMost(watchdogProps.restartBackoffMaxMs)
    }

    private suspend fun stopProcess() {
        val p = process ?: return
        process = null

        log.info("Stopping OpenCode server")
        p.destroy()
        try {
            withTimeout(5.seconds) {
                while (p.isAlive) {
                    delay(50.milliseconds)
                }
            }
        } catch (_: TimeoutCancellationException) {
            log.warn("OpenCode server did not stop gracefully, forcing termination")
            p.descendants().forEach { it.destroyForcibly() }
            p.destroyForcibly()
        }
        log.info("OpenCode server stopped")
    }

    private suspend fun logStream(stream: java.io.InputStream, error: Boolean) {
        withContext(Dispatchers.IO) {
            BufferedReader(InputStreamReader(stream)).use { reader ->
                reader.lines().forEach { line ->
                    if (isActive) {
                        if (error) {
                            log.error("opencode> {}", line)
                        } else {
                            log.info("opencode> {}", line)
                        }
                    }
                }
            }
        }
    }

    override fun close() {
        closing = true
        runBlocking {
            stopProcess()
        }
        scope.cancel()
    }
}
