package eu.darken.apl.watch.core.server

import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.access.AccessState
import eu.darken.apl.watch.core.WatchId
import eu.darken.apl.watch.core.db.WatchDatabase
import eu.darken.apl.watch.core.db.server.WatchServerStateEntity
import eu.darken.apl.watch.core.types.Watch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the server's watch list monitors the watches, and which watches the app still checks
 * itself: its own checks, their replays and the history pages.
 */
@Singleton
class WatchServerMode @Inject constructor(
    private val accessRepo: AccessRepo,
    private val watchDb: WatchDatabase,
) {

    data class State(
        /** The access this was decided for. */
        val access: AccessState?,
        /** The access offers the watch list, and the stored server state is for its installation or there is none. */
        val isActive: Boolean,
        /** Only filled in server mode. */
        val states: Map<WatchId, WatchServerStateEntity> = emptyMap(),
    ) {

        /** Everything is checked by the app outside server mode, in it only until the server holds the watch. */
        fun isLegacy(watch: Watch): Boolean {
            if (!isActive) return true
            return WatchServerStateEntity.isAppChecked(states[watch.id])
        }

        /**
         * Null outside server mode and for a watch the server was never told about. While the server holds
         * an older definition of [watch], its presence and last match say nothing about the current one.
         */
        fun serverStatus(watch: Watch): WatchServerStatus? {
            val state = states[watch.id]?.takeIf { isActive } ?: return null
            val status = WatchServerStatus.from(state)
            if (!status.isHeld || state.ackedDefinitionRevision == watch.definitionRevision) return status
            return status.copy(presence = WatchServerStatus.Presence.UNKNOWN, lastMatch = null)
        }

        companion object {
            val OFF = State(access = null, isActive = false)
        }
    }

    val state: Flow<State> = accessRepo.state
        .flatMapLatest { access ->
            if (access?.offersWatchList != true) {
                flowOf(State(access, isActive = false))
            } else {
                combine(
                    watchDb.server.otherInstallation(access.installationId),
                    watchDb.server.allStates(),
                ) { other, states ->
                    if (other) {
                        State(access, isActive = false)
                    } else {
                        State(access, isActive = true, states = states.associateBy { it.watchId })
                    }
                }
            }
        }
        .distinctUntilChanged()

    suspend fun current(): State {
        accessRepo.awaitLoaded()
        val access = accessRepo.state.value
        if (access?.offersWatchList != true) return State(access, isActive = false)
        if (watchDb.server.hasOtherInstallation(access.installationId)) return State(access, isActive = false)
        return State(access, isActive = true, states = watchDb.server.getAllStates().associateBy { it.watchId })
    }
}
