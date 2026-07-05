package io.github.torvehammok.infra.opencode

import io.github.ktor_batterypack.core.health.HealthCheckResult
import io.github.ktor_batterypack.core.health.HealthStatus
import io.github.ktor_batterypack.core.health.ReadinessCheck
import org.slf4j.LoggerFactory

private const val OPEN_CODE = "openCode"

private val log = LoggerFactory.getLogger(OpenCodeReadinessCheck::class.java)

class OpenCodeReadinessCheck(private val openCodeClient: OpenCodeClient) : ReadinessCheck {

    override suspend fun check(): HealthCheckResult {
        try {
            val status = openCodeClient.fetchHealthcheckStatus()

            if (status.value in 200..<300) {
                return HealthCheckResult(OPEN_CODE, HealthStatus.UP)
            }
            return HealthCheckResult(OPEN_CODE, HealthStatus.DOWN)
        } catch (ex: Exception) {
            log.trace("Error while opencode server readiness check", ex)
            return HealthCheckResult(OPEN_CODE, HealthStatus.DOWN)

        }
    }

}