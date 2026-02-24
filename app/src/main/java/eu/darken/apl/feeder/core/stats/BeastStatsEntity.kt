package eu.darken.apl.feeder.core.stats

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import eu.darken.apl.feeder.core.ReceiverId
import java.time.Instant

@Entity(
    tableName = "stats_beast",
    indices = [Index(value = ["receiver_id", "received_at"])]
)
data class BeastStatsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "receiver_id") val receiverId: ReceiverId,
    @ColumnInfo(name = "received_at") val receivedAt: Instant = Instant.now(),
    @ColumnInfo(name = "position_rate") val positionRate: Double,
    @ColumnInfo(name = "positions") val positions: Int,
    @ColumnInfo(name = "message_rate") val messageRate: Double,
    @ColumnInfo(name = "bandwidth") val bandwidth: Double,
    @ColumnInfo(name = "connection_time") val connectionTime: Long,
    @ColumnInfo(name = "latency") val latency: Long,
)