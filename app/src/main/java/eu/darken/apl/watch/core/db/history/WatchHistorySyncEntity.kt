package eu.darken.apl.watch.core.db.history

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import eu.darken.apl.watch.core.WatchId
import java.time.Instant

/**
 * How far the server's history has been fetched for one watch. While an evaluation is paged,
 * [windowFrom], [windowUntil] and [cursorJson] hold it: a cursor is only valid with the range it
 * came from.
 */
@Entity(tableName = "watch_history_sync")
data class WatchHistorySyncEntity(
    @PrimaryKey @ColumnInfo(name = "watch_id") val watchId: WatchId,
    @ColumnInfo(name = "definition_revision") val definitionRevision: Long,
    @ColumnInfo(name = "window_from") val windowFrom: Instant? = null,
    @ColumnInfo(name = "window_until") val windowUntil: Instant? = null,
    @ColumnInfo(name = "cursor_json") val cursorJson: String? = null,
    @ColumnInfo(name = "last_seen_at") val lastSeenAt: Instant? = null,
    @ColumnInfo(name = "last_seen_aircraft_id") val lastSeenAircraftId: String? = null,
    @ColumnInfo(name = "evaluation_basis") val evaluationBasis: String? = null,
    /** The tier that refused this watch; it is not asked again until the tier changes. */
    @ColumnInfo(name = "restricted_tier") val restrictedTier: String? = null,
    @ColumnInfo(name = "next_eligible_at") val nextEligibleAt: Instant,
    @ColumnInfo(name = "backoff_step") val backoffStep: Int = 0,
    @ColumnInfo(name = "last_pass_at") val lastPassAt: Instant? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)
