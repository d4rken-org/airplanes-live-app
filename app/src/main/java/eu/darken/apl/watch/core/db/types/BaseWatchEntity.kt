package eu.darken.apl.watch.core.db.types

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import eu.darken.apl.watch.core.WatchId
import eu.darken.apl.watch.core.makeWatchId
import java.time.Instant

@Entity(tableName = "watch_base")
data class BaseWatchEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: WatchId = makeWatchId(),
    @ColumnInfo(name = "created_at") val createdAt: Instant = Instant.now(),
    @ColumnInfo(name = "type") val watchType: String,
    @ColumnInfo(name = "notification_enabled") val notificationEnabled: Boolean = false,

    @ColumnInfo(name = "user_note") val userNote: String = "",

    @ColumnInfo(name = "location_latitude") val latitude: Double? = null,
    @ColumnInfo(name = "location_longitude") val longitude: Double? = null,
    @ColumnInfo(name = "location_radius") val radius: Float? = null,

    @ColumnInfo(name = "last_check_at") val lastCheckAt: Instant? = null,
    @ColumnInfo(name = "last_check_outcome") val lastCheckOutcome: String? = null,
    @ColumnInfo(name = "last_check_reason") val lastCheckReason: String? = null,
)
