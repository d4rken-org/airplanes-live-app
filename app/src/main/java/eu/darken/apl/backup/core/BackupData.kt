package eu.darken.apl.backup.core

import eu.darken.apl.feeder.core.config.FeederConfig
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class BackupData(
    @SerialName("version") val version: Int,
    @Contextual @SerialName("createdAt") val createdAt: Instant,
    @SerialName("appVersion") val appVersion: String,
    @SerialName("appVersionCode") val appVersionCode: Long,
    @SerialName("watches") val watches: WatchBackup? = null,
    @SerialName("feeders") val feeders: FeederBackup? = null,
    // Written by older versions, kept so their archives still parse. Never exported or restored.
    @SerialName("apiKey") val apiKey: String? = null,
    // Written by older versions. Only registrations are read, for watches saved without one.
    @SerialName("aircraftCache") val aircraftCache: LegacyAircraftCache? = null,
)

@Serializable
data class WatchBackup(
    @SerialName("items") val items: List<WatchItemBackup>,
    // Older versions require the key to parse a backup. Exported empty, ignored on restore.
    @SerialName("checks") val checks: List<WatchCheckBackup> = emptyList(),
)

@Serializable
data class WatchItemBackup(
    @SerialName("type") val type: String,
    @Contextual @SerialName("createdAt") val createdAt: Instant,
    @SerialName("notificationEnabled") val notificationEnabled: Boolean = false,
    @SerialName("userNote") val userNote: String = "",
    @SerialName("hexCode") val hexCode: String? = null,
    @SerialName("lastRegistration") val lastRegistration: String? = null,
    @SerialName("callsign") val callsign: String? = null,
    @SerialName("squawkCode") val squawkCode: String? = null,
    @SerialName("locationLabel") val locationLabel: String? = null,
    @SerialName("latitude") val latitude: Double? = null,
    @SerialName("longitude") val longitude: Double? = null,
    @SerialName("radiusInMeters") val radiusInMeters: Float? = null,
    @SerialName("minAltitudeFt") val minAltitudeFt: Int? = null,
    @SerialName("maxAltitudeFt") val maxAltitudeFt: Int? = null,
)

@Serializable
data class WatchCheckBackup(
    @SerialName("watchIndex") val watchIndex: Int,
    @Contextual @SerialName("checkedAt") val checkedAt: Instant,
    @SerialName("aircraftCount") val aircraftCount: Int,
    @SerialName("seenHexes") val seenHexes: String? = null,
)

@Serializable
data class FeederBackup(
    @SerialName("configs") val configs: List<FeederConfig>,
    @SerialName("beastStats") val beastStats: List<BeastStatBackup>,
    @SerialName("mlatStats") val mlatStats: List<MlatStatBackup>,
)

@Serializable
data class BeastStatBackup(
    @SerialName("receiverId") val receiverId: String,
    @Contextual @SerialName("receivedAt") val receivedAt: Instant,
    @SerialName("positionRate") val positionRate: Double,
    @SerialName("positions") val positions: Int,
    @SerialName("messageRate") val messageRate: Double,
    @SerialName("bandwidth") val bandwidth: Double,
    @SerialName("connectionTime") val connectionTime: Long,
    @SerialName("latency") val latency: Long,
)

@Serializable
data class MlatStatBackup(
    @SerialName("receiverId") val receiverId: String,
    @Contextual @SerialName("receivedAt") val receivedAt: Instant,
    @SerialName("messageRate") val messageRate: Double,
    @SerialName("peerCount") val peerCount: Int,
    @SerialName("badSyncTimeout") val badSyncTimeout: Long,
    @SerialName("outlierPercent") val outlierPercent: Float,
)

@Serializable
data class LegacyAircraftCache(
    @SerialName("items") val items: List<LegacyAircraftCacheItem>,
)

@Serializable
data class LegacyAircraftCacheItem(
    @SerialName("hex") val hex: String,
    @SerialName("registration") val registration: String? = null,
)
