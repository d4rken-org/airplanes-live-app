package eu.darken.apl.watch.core

import android.os.SystemClock
import eu.darken.apl.main.core.aircraft.AircraftHex
import eu.darken.apl.watch.core.types.AircraftWatch
import java.util.concurrent.atomic.AtomicReference

/** One tap adds an aircraft that has no watch yet, otherwise opens its watch. One per screen. */
class AircraftQuickWatch(
    private val watchRepo: WatchRepo,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) {
    /** The hex [toggle] last added, with [clock] at that moment. */
    private val lastAdd = AtomicReference<Pair<AircraftHex, Long>?>(null)

    suspend fun toggle(hex: AircraftHex): Result {
        val result = try {
            watchRepo.addAircraft(hex)
        } catch (e: WatchLimitReachedException) {
            return Result.LimitReached(e)
        }
        return when (result) {
            is WatchRepo.AircraftAdd.Created -> {
                lastAdd.set(result.watch.hex to clock())
                Result.Added(result.watch)
            }

            is WatchRepo.AircraftAdd.Existing -> {
                // The second tap of a double tap finds the watch the first one just added
                val (lastHex, addedAt) = lastAdd.get() ?: (null to 0L)
                val repeated = lastHex.equals(result.watch.hex, ignoreCase = true) &&
                        clock() - addedAt < QUICK_ADD_REPEAT_MS
                if (repeated) Result.Repeated else Result.Open(result.watch.id)
            }
        }
    }

    suspend fun undo(id: WatchId) {
        lastAdd.set(null)
        watchRepo.delete(id)
    }

    sealed interface Result {
        data class Added(val watch: AircraftWatch) : Result
        data class Open(val watchId: WatchId) : Result
        data object Repeated : Result
        data class LimitReached(val error: WatchLimitReachedException) : Result
    }

    companion object {
        private const val QUICK_ADD_REPEAT_MS = 1_000L
    }
}
