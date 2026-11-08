package io.github.torvehammok.domain.dto

import java.math.BigDecimal

/**
 * opencode session bound to a Slack thread.
 *
 * @param lastTs ts of the newest thread message already sent to the session
 * @param costTotal running cost of the whole session, reported by opencode
 */
data class ThreadSession(
    val sessionId: String,
    val lastTs: String,
    val costTotal: BigDecimal
)

/**
 * @param session session to reuse for the next message in the thread, null when it should not be reused
 */
data class OcAgentRun(
    val response: OcAgentResponse,
    val session: ThreadSession?
)
