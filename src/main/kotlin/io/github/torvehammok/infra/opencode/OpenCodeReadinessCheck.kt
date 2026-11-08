package io.github.torvehammok.infra.opencode

import io.github.ktor_batterypack.core.health.HealthCheckResult
import io.github.ktor_batterypack.core.health.HealthStatus
import io.github.ktor_batterypack.core.health.ReadinessCheck
import org.slf4j.LoggerFactory

private const val OPEN_CODE = "openCode"

private val log = LoggerFactory.getLogger(OpenCodeReadinessCheck::class.java)

/**
 * `/api/info` alone stays green while a session executor is wedged - the server is reported down
 * while it is being (re)started after a stall or crash.
 */
class OpenCodeReadinessCheck(
    private val openCodeClient: OpenCodeClient,
    private val openCodeHealth: OpenCodeHealth
) : ReadinessCheck {

    @Volatile
    private var lastStatus: HealthStatus? = null

    override suspend fun check(): HealthCheckResult {
        val processState = openCodeHealth.processState
        if (!openCodeHealth.isServerAvailable) {
            return result(HealthStatus.DOWN, "opencode server is ${processState.name.lowercase()}")
        }

        try {
            val status = openCodeClient.fetchHealthcheckStatus()

            if (status.value in 200..<300) {
                return result(HealthStatus.UP, "opencode server is healthy")
            }
            return result(HealthStatus.DOWN, "opencode server healthcheck returned $status")
        } catch (ex: Exception) {
            log.trace("Error while opencode server readiness check", ex)
            return result(HealthStatus.DOWN, "opencode server healthcheck failed: ${ex.message}")
        }
    }

    private fun result(status: HealthStatus, reason: String): HealthCheckResult {
        if (lastStatus != status) {
            log.info("OpenCode readiness changed to {}: {}", status, reason)
            lastStatus = status
        }
        return HealthCheckResult(OPEN_CODE, status)
    }

}
