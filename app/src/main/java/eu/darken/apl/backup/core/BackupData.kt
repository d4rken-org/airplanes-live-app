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
    @SerialName("apiKey") val apiKey: String? = null,
    @SerialName("aircraftCache") val aircraftCache: AircraftCacheBackup? = null,
)

@Serializable
data class WatchBackup(
    @SerialName("items") val items: List<WatchItemBackup>,
    @SerialName("checks") val checks: List<WatchCheckBackup>,
)

@Serializable
data class WatchItemBackup(
    @SerialName("type") val type: String,
    @Contextual @SerialName("createdAt") val createdAt: Instant,
    @SerialName("notificationEnabled") val notificationEnabled: Boolean = false,
    @SerialName("userNote") val userNote: String = "",
    @SerialName("hexCode") val hexCode: String? = null,
    @SerialName("callsign") val callsign: String? = null,
    @SerialName("squawkCode") val squawkCode: String? = null,
    @SerialName("locationLabel") val locationLabel: String? = null,
    @SerialName("latitude") val latitude: Double? = null,
    @SerialName("longitude") val longitude: Double? = null,
    @SerialName("radiusInMeters") val radiusInMeters: Float? = null,
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
data class AircraftCacheBackup(
    @SerialName("items") val items: List<AircraftCacheItemBackup>,
)

@Serializable
data class AircraftCacheItemBackup(
    @SerialName("hex") val hex: String,
    @SerialName("messageType") val messageType: String,
    @SerialName("dbFlags") val dbFlags: Int? = null,
    @SerialName("registration") val registration: String? = null,
    @SerialName("callsign") val callsign: String? = null,
    @SerialName("operator") val operator: String? = null,
    @SerialName("airframe") val airframe: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("squawk") val squawk: String? = null,
    @SerialName("emergency") val emergency: String? = null,
    @SerialName("outsideTemp") val outsideTemp: Int? = null,
    @SerialName("altitude") val altitude: String? = null,
    @SerialName("altitudeRate") val altitudeRate: Int? = null,
    @SerialName("groundSpeed") val groundSpeed: Float? = null,
    @SerialName("indicatedAirSpeed") val indicatedAirSpeed: Int? = null,
    @SerialName("trackheading") val trackheading: Double? = null,
    @SerialName("groundTrack") val groundTrack: Float? = null,
    @SerialName("latitude") val latitude: Double? = null,
    @SerialName("longitude") val longitude: Double? = null,
    @SerialName("messages") val messages: Int = 0,
    @Contextual @SerialName("seenAt") val seenAt: Instant,
    @SerialName("rssi") val rssi: Double = 0.0,
)
