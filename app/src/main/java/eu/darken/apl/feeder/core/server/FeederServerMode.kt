package eu.darken.apl.feeder.core.server

import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.access.AccessState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Whether the server monitors the feeders, or the app still polls them itself. */
@Singleton
class FeederServerMode @Inject constructor(
    private val accessRepo: AccessRepo,
    private val store: FeederServerStore,
) {

    data class State(
        /** The access this was decided for. */
        val access: AccessState?,
        /** The access offers feeder monitoring, and the stored server state is for its installation or there is none. */
        val isActive: Boolean,
        /** Only filled in server mode. */
        val server: FeederServerState? = null,
    )

    val state: Flow<State> = accessRepo.state
        .flatMapLatest { access ->
            if (access?.offersFeederMonitor != true) {
                flowOf(State(access, isActive = false))
            } else {
                store.state.map { decide(access, it) }
            }
        }
        .distinctUntilChanged()

    suspend fun current(): State {
        accessRepo.awaitLoaded()
        val access = accessRepo.state.value
        if (access?.offersFeederMonitor != true) return State(access, isActive = false)
        return decide(access, store.current())
    }

    private fun decide(access: AccessState, server: FeederServerState): State {
        val other = server.installationId != null && server.installationId != access.installationId
        return if (other) State(access, isActive = false) else State(access, isActive = true, server = server)
    }
}
