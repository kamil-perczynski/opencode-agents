package io.github.torvehammok.infra.opencode

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.koin.core.annotation.Singleton

enum class OpenCodeProcessState {
    /** server is not spawned by this app (e.g. tests, external server) */
    UNMANAGED,
    STARTING,
    RUNNING,
    RESTARTING
}

data class OpenCodeRestartRequest(val reason: String)

/**
 * Shared opencode server state. Lets the agent request a server restart without depending on
 * [OpenCodeServerManager], which only exists when the server is spawned by this app.
 */
@Singleton
class OpenCodeHealth {

    @Volatile
    var processState: OpenCodeProcessState = OpenCodeProcessState.UNMANAGED

    // concurrent stalls collapse into a single pending restart request
    private val _restartRequests = MutableSharedFlow<OpenCodeRestartRequest>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val restartRequests: SharedFlow<OpenCodeRestartRequest> = _restartRequests.asSharedFlow()

    val isServerAvailable: Boolean
        get() = processState == OpenCodeProcessState.UNMANAGED || processState == OpenCodeProcessState.RUNNING

    fun reportStall(sessionId: String) {
        _restartRequests.tryEmit(OpenCodeRestartRequest("session $sessionId has stalled"))
    }

}
