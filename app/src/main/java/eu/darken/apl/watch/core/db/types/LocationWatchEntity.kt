package eu.darken.apl.watch.core.db.types

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import eu.darken.apl.watch.core.WatchId

@Entity(
    tableName = "watch_location",
    foreignKeys = [
        ForeignKey(
            entity = BaseWatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class LocationWatchEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: WatchId,
    @ColumnInfo(name = "label") val label: String,
) : WatchType {
    companion object {
        const val TYPE_KEY = "location"
    }
}
