package eu.darken.apl.server.status

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import eu.darken.apl.common.datastore.createValue
import eu.darken.apl.common.datastore.value
import eu.darken.apl.common.debug.logging.Logging.Priority.ERROR
import eu.darken.apl.common.debug.logging.Logging.Priority.INFO
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.ServerCompatDataStore
import eu.darken.apl.server.api.MetaResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** The maintenance the server announced in its latest `/meta` answer, and the one the user dismissed. */
@Singleton
class PlannedMaintenanceRepo @Inject constructor(
    @ServerCompatDataStore dataStore: DataStore<Preferences>,
    json: Json,
    private val serverClock: ServerClock,
) {

    private val notice = dataStore.createValue<StoredNotice?>(
        key = KEY_NOTICE,
        defaultValue = null,
        json = json,
        onErrorFallbackToDefault = true,
    )

    private val dismissed = dataStore.createValue<String?>(
        key = KEY_DISMISSED,
        defaultValue = null,
        json = json,
        onErrorFallbackToDefault = true,
    )

    /** The stored announcement, whether or not it is due. */
    val announced: Flow<PlannedMaintenance?> = notice.flow
        .map { it?.toNotice() }
        .catch { e ->
            log(TAG, ERROR) { "Reading the announced maintenance failed: ${e.asLog()}" }
            emit(null)
        }

    private val dismissedId: Flow<String?> = dismissed.flow.catch { e ->
        log(TAG, ERROR) { "Reading the dismissed maintenance failed: ${e.asLog()}" }
        emit(null)
    }

    /**
     * The announcement to show now: from [LEAD] before it starts until it starts, unless its id was
     * dismissed. Moves across those bounds on its own, by [ServerClock] time.
     */
    val upcoming: Flow<PlannedMaintenance?> = combine(announced, dismissedId) { stored, hiddenId ->
        stored?.takeIf { it.id != hiddenId }
    }
        .distinctUntilChanged()
        .flatMapLatest { candidate -> if (candidate == null) flowOf(null) else window(candidate) }
        .distinctUntilChanged()

    private fun window(candidate: PlannedMaintenance): Flow<PlannedMaintenance?> = flow {
        val showFrom = candidate.startsAt.minus(LEAD)
        while (true) {
            val now = serverClock.now()
            emit(candidate.takeIf { now >= showFrom && now < candidate.startsAt })
            val boundary = when {
                now < showFrom -> showFrom
                now < candidate.startsAt -> candidate.startsAt
                else -> null
            }
            // Capped, so a server clock anchored anew is picked up soon
            val wait = boundary
                ?.let { Duration.between(now, it).toMillis().coerceAtLeast(1) }
                ?.coerceAtMost(MAX_WAIT.toMillis())
                ?: MAX_WAIT.toMillis()
            delay(wait)
        }
    }

    /** The server answered `/meta`: its notice replaces the stored one, an absent or malformed one clears it. */
    suspend fun onMetaAnswered(meta: MetaResponse) {
        val parsed = PlannedMaintenance.parse(meta.maintenance)
        try {
            val updated = notice.update { parsed?.let { StoredNotice.of(it) } }
            if (updated.old != updated.new) log(TAG, INFO) { "Announced maintenance: $parsed" }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(TAG, ERROR) { "Storing the announced maintenance failed: ${e.asLog()}" }
        }
    }

    suspend fun dismiss(id: String) {
        log(TAG) { "dismiss($id)" }
        try {
            dismissed.value(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(TAG, ERROR) { "Storing the dismissed maintenance failed: ${e.asLog()}" }
        }
    }

    @Serializable
    internal data class StoredNotice(
        val id: String,
        val startsAtMillis: Long,
        val endsAtMillis: Long,
    ) {
        fun toNotice() = PlannedMaintenance(
            id = id,
            startsAt = Instant.ofEpochMilli(startsAtMillis),
            endsAt = Instant.ofEpochMilli(endsAtMillis),
        )

        companion object {
            fun of(notice: PlannedMaintenance) = StoredNotice(
                id = notice.id,
                startsAtMillis = notice.startsAt.toEpochMilli(),
                endsAtMillis = notice.endsAt.toEpochMilli(),
            )
        }
    }

    companion object {
        /** How long before its start an announced maintenance is shown. */
        val LEAD: Duration = Duration.ofHours(24)
        internal val MAX_WAIT: Duration = Duration.ofSeconds(60)
        private const val KEY_NOTICE = "maintenance.planned"
        private const val KEY_DISMISSED = "maintenance.planned.dismissed"
        private val TAG = logTag("Server", "Status", "Maintenance")
    }
}
