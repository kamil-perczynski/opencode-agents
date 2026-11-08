package io.github.torvehammok.infra.opencode

open class OpenCodeClientException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * The session does not exist on the server, e.g. it was lost with the server's data directory.
 */
class OpenCodeSessionNotFoundException(sessionId: String) : OpenCodeClientException("Session $sessionId not found")
