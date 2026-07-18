package io.github.torvehammok.infra.opencode

import io.github.ktor_batterypack.core.di.InitCallback
import io.github.torvehammok.domain.OpenCodeProps
import io.github.torvehammok.infra.git.SSHGitConfigFetcher
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val log = LoggerFactory.getLogger(OpenCodeServerManager::class.java)

class OpenCodeServerManager(
    private val opencodeProps: OpenCodeProps,
    private val sshGitConfigFetcher: SSHGitConfigFetcher
) : AutoCloseable, InitCallback {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineName("opencode-server"))

    private var process: Process? = null

    override fun onInit() {
        if (opencodeProps.server.enabled) {
            sshGitConfigFetcher.gitClone()
            start()
        }
    }

    fun start(): Process {
        check(process == null) { "OpenCode server is already running" }

        val command = listOf(
            opencodeProps.opencodeBinary,
            "serve",
            "--hostname", "0.0.0.0",
            "--port", opencodeProps.server.port.toString()
        )

        log.info("Starting OpenCode server: {}", command.joinToString(" "))

        val builder = ProcessBuilder(command)
            .redirectErrorStream(false)

        val env = builder.environment()

        opencodeProps.server.env.custom.forEach { (key, value) ->
            env[key] = value
        }

        val started = builder.start()
        process = started

        scope.launch(CoroutineName("opencode-stdout")) {
            logStream(started.inputStream, false)
        }
        scope.launch(CoroutineName("opencode-stderr")) {
            logStream(started.errorStream, true)
        }

        return started
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
        val p = process ?: return
        process = null

        log.info("Stopping OpenCode server")
        p.destroy()
        runBlocking {
            try {
                withTimeout(5.seconds) {
                    while (p.isAlive) {
                        delay(50.milliseconds)
                    }
                }
            } catch (_: TimeoutCancellationException) {
                log.warn("OpenCode server did not stop gracefully, forcing termination")
                p.destroyForcibly()
            }
            scope.cancel()
        }

        log.info("OpenCode server stopped")
    }
}
