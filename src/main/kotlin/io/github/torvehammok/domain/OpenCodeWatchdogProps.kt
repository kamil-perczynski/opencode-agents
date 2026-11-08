package io.github.torvehammok.domain

import com.fasterxml.jackson.annotation.JsonPropertyDescription

data class OpenCodeWatchdogProps(
    @param:JsonPropertyDescription("Max time to wait for the event stream to connect, in milliseconds")
    val eventStreamConnectTimeoutMs: Long = 10_000,
    @param:JsonPropertyDescription("A running session without any event for this long is considered stalled, in milliseconds")
    val inactivityTimeoutMs: Long = 120_000,
    @param:JsonPropertyDescription("How often the watchdog checks session liveness, in milliseconds")
    val checkIntervalMs: Long = 5_000,
    @param:JsonPropertyDescription("Event stream without heartbeat for this long is considered dead and reconnected, in milliseconds")
    val heartbeatTimeoutMs: Long = 30_000,
    @param:JsonPropertyDescription("Max consecutive failed event stream reconnection attempts")
    val maxReconnectAttempts: Int = 5,
    @param:JsonPropertyDescription("Initial event stream reconnection backoff, doubled on each attempt, in milliseconds")
    val reconnectBackoffMs: Long = 500,
    @param:JsonPropertyDescription("Max event stream reconnection backoff, in milliseconds")
    val reconnectBackoffMaxMs: Long = 5_000,
    @param:JsonPropertyDescription("Min time between opencode server restarts caused by stalled sessions, in milliseconds")
    val restartCooldownMs: Long = 30_000,
    @param:JsonPropertyDescription("Max time for a (re)started opencode server to become healthy, in milliseconds")
    val startupTimeoutMs: Long = 30_000,
    @param:JsonPropertyDescription("Max backoff between consecutive opencode server restarts, in milliseconds")
    val restartBackoffMaxMs: Long = 60_000,
    @param:JsonPropertyDescription("Restart backoff resets once the opencode server runs this long, in milliseconds")
    val backoffResetAfterMs: Long = 300_000
)
