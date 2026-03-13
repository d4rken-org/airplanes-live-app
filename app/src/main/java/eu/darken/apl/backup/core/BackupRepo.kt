package eu.darken.apl.backup.core

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.apl.common.BuildConfigWrap
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.datastore.value
import eu.darken.apl.common.debug.logging.Logging.Priority.ERROR
import eu.darken.apl.common.debug.logging.Logging.Priority.INFO
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.feeder.core.config.FeederSettings
import eu.darken.apl.feeder.core.stats.BeastStatsEntity
import eu.darken.apl.feeder.core.stats.FeederStatsDatabase
import eu.darken.apl.feeder.core.stats.MlatStatsEntity
import eu.darken.apl.main.core.GeneralSettings
import eu.darken.apl.main.core.db.AircraftDatabase
import eu.darken.apl.main.core.db.CachedAircraftEntity
import eu.darken.apl.watch.core.WatchId
import eu.darken.apl.watch.core.db.WatchDatabase
import eu.darken.apl.watch.core.db.history.WatchCheckEntity
import eu.darken.apl.watch.core.db.types.AircraftWatchEntity
import eu.darken.apl.watch.core.db.types.BaseWatchEntity
import eu.darken.apl.watch.core.db.types.FlightWatchEntity
import eu.darken.apl.watch.core.db.types.LocationWatchEntity
import eu.darken.apl.watch.core.db.types.SquawkWatchEntity
import eu.darken.apl.watch.core.makeWatchId
import eu.darken.apl.watch.core.types.AircraftWatch
import eu.darken.apl.watch.core.types.FlightWatch
import eu.darken.apl.watch.core.types.LocationWatch
import eu.darken.apl.watch.core.types.SquawkWatch
import eu.darken.apl.watch.core.types.Watch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepo @Inject constructor(
    @ApplicationContext private val context: Context,
    private val watchDatabase: WatchDatabase,
    private val feederSettings: FeederSettings,
    private val feederStatsDatabase: FeederStatsDatabase,
    private val generalSettings: GeneralSettings,
    private val aircraftDatabase: AircraftDatabase,
    private val json: Json,
    private val dispatcherProvider: DispatcherProvider,
) {

    enum class BackupStep {
        WATCHES,
        FEEDERS,
        AIRCRAFT_CACHE,
        API_KEY,
        WRITING_FILE,
        READING_FILE,
    }

    @Volatile
    private var cachedBackupData: BackupData? = null

    data class BackupPreview(
        val watchCount: Int,
        val checkCount: Int,
        val feederCount: Int,
        val statsCount: Int,
        val hasApiKey: Boolean,
        val aircraftCacheCount: Int,
    )

    data class BackupOptions(
        val includeWatches: Boolean = true,
        val includeFeeders: Boolean = true,
        val includeApiKey: Boolean = true,
        val includeAircraftCache: Boolean = true,
    )

    data class RestorePreview(
        val appVersion: String,
        val appVersionCode: Long,
        val createdAt: Instant,
        val watchCount: Int,
        val checkCount: Int,
        val feederCount: Int,
        val statsCount: Int,
        val hasApiKey: Boolean,
        val aircraftCacheCount: Int,
        val versionMismatch: Boolean,
    )

    data class RestoreOptions(
        val includeWatches: Boolean = true,
        val includeFeeders: Boolean = true,
        val includeApiKey: Boolean = true,
        val includeAircraftCache: Boolean = true,
    )

    data class RestoreResult(
        val watchesImported: Int = 0,
        val watchesExisted: Int = 0,
        val checksImported: Int = 0,
        val checksExisted: Int = 0,
        val feedersImported: Int = 0,
        val feedersExisted: Int = 0,
        val statsImported: Int = 0,
        val apiKeyImported: Boolean = false,
        val aircraftCacheImported: Int = 0,
        val aircraftCacheExisted: Int = 0,
        val errors: List<String> = emptyList(),
    )

    suspend fun getBackupPreview(): BackupPreview = withContext(dispatcherProvider.IO) {
        val apiKey = generalSettings.airplanesLiveApiKey.value()
        BackupPreview(
            watchCount = watchDatabase.watchCount(),
            checkCount = watchDatabase.checks.count(),
            feederCount = feederSettings.feederGroup.value().configs.size,
            statsCount = feederStatsDatabase.beastStats.count() + feederStatsDatabase.mlatStats.count(),
            hasApiKey = !apiKey.isNullOrBlank(),
            aircraftCacheCount = aircraftDatabase.count(),
        )
    }

    suspend fun createBackup(
        uri: Uri,
        options: BackupOptions,
        onProgress: (suspend (BackupStep) -> Unit)? = null,
    ) = withContext(dispatcherProvider.IO) {
        log(TAG) { "createBackup($uri, $options)" }

        onProgress?.invoke(BackupStep.WATCHES)
        val watchBackup = if (options.includeWatches) {
            val watches = watchDatabase.watches.first()
            val watchIdToIndex = mutableMapOf<WatchId, Int>()

            val items = watches.mapIndexed { index, watch ->
                watchIdToIndex[watch.id] = index
                watch.toBackupItem()
            }

            val allChecks = watchDatabase.checks.getAll()
            val checks = allChecks.mapNotNull { check ->
                val idx = watchIdToIndex[check.watchId] ?: return@mapNotNull null
                WatchCheckBackup(
                    watchIndex = idx,
                    checkedAt = check.checkedAt,
                    aircraftCount = check.aircraftcount,
                    seenHexes = check.seenHexes,
                )
            }

            WatchBackup(items = items, checks = checks)
        } else null

        onProgress?.invoke(BackupStep.FEEDERS)
        val feederBackup = if (options.includeFeeders) {
            val configs = feederSettings.feederGroup.value().configs.toList()
            val beastStats = feederStatsDatabase.beastStats.getAll().map { it.toBackup() }
            val mlatStats = feederStatsDatabase.mlatStats.getAll().map { it.toBackup() }
            FeederBackup(configs = configs, beastStats = beastStats, mlatStats = mlatStats)
        } else null

        onProgress?.invoke(BackupStep.AIRCRAFT_CACHE)
        val aircraftCacheBackup = if (options.includeAircraftCache) {
            val cached = aircraftDatabase.current().first()
            if (cached.isNotEmpty()) {
                AircraftCacheBackup(items = cached.map { it.toAircraftCacheBackup() })
            } else null
        } else null

        onProgress?.invoke(BackupStep.API_KEY)
        val apiKey = if (options.includeApiKey) generalSettings.airplanesLiveApiKey.value() else null

        onProgress?.invoke(BackupStep.WRITING_FILE)
        val backupData = BackupData(
            version = BACKUP_VERSION,
            createdAt = Instant.now(),
            appVersion = BuildConfigWrap.VERSION_NAME,
            appVersionCode = BuildConfigWrap.VERSION_CODE,
            watches = watchBackup,
            feeders = feederBackup,
            apiKey = apiKey,
            aircraftCache = aircraftCacheBackup,
        )

        val jsonString = json.encodeToString(BackupData.serializer(), backupData)

        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            ZipOutputStream(outputStream).use { zipOut ->
                zipOut.putNextEntry(ZipEntry(BACKUP_ENTRY_NAME))
                zipOut.write(jsonString.toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()
            }
        } ?: throw IllegalStateException("Could not open output stream for $uri")

        log(TAG, INFO) { "Backup created successfully" }
    }

    suspend fun readBackup(uri: Uri): RestorePreview = withContext(dispatcherProvider.IO) {
        log(TAG) { "readBackup($uri)" }

        val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
            ZipInputStream(inputStream).use { zipIn ->
                val entry = zipIn.nextEntry ?: throw IllegalArgumentException("Empty zip file")

                if (entry.name != BACKUP_ENTRY_NAME) {
                    throw IllegalArgumentException("Invalid backup: expected $BACKUP_ENTRY_NAME, got ${entry.name}")
                }

                val bytes = zipIn.readBytesLimited(MAX_UNCOMPRESSED_SIZE)

                if (zipIn.nextEntry != null) {
                    throw IllegalArgumentException("Invalid backup: zip contains multiple entries")
                }

                String(bytes, Charsets.UTF_8)
            }
        } ?: throw IllegalStateException("Could not open input stream for $uri")

        val data = json.decodeFromString(BackupData.serializer(), jsonString)

        if (data.version > BACKUP_VERSION) {
            throw IllegalArgumentException("Unsupported backup version ${data.version}. Please update the app.")
        }

        cachedBackupData = data

        log(TAG, INFO) {
            "Parsed backup: version=${data.version}, watches=${data.watches?.items?.size ?: 0}, " +
                    "checks=${data.watches?.checks?.size ?: 0}, feeders=${data.feeders?.configs?.size ?: 0}, " +
                    "beastStats=${data.feeders?.beastStats?.size ?: 0}, mlatStats=${data.feeders?.mlatStats?.size ?: 0}, " +
                    "hasApiKey=${!data.apiKey.isNullOrBlank()}, aircraftCache=${data.aircraftCache?.items?.size ?: 0}"
        }

        RestorePreview(
            appVersion = data.appVersion,
            appVersionCode = data.appVersionCode,
            createdAt = data.createdAt,
            watchCount = data.watches?.items?.size ?: 0,
            checkCount = data.watches?.checks?.size ?: 0,
            feederCount = data.feeders?.configs?.size ?: 0,
            statsCount = (data.feeders?.beastStats?.size ?: 0) + (data.feeders?.mlatStats?.size ?: 0),
            hasApiKey = !data.apiKey.isNullOrBlank(),
            aircraftCacheCount = data.aircraftCache?.items?.size ?: 0,
            versionMismatch = data.appVersionCode != BuildConfigWrap.VERSION_CODE,
        )
    }

    suspend fun restoreBackup(
        options: RestoreOptions,
        onProgress: (suspend (BackupStep) -> Unit)? = null,
    ): RestoreResult = withContext(NonCancellable + dispatcherProvider.IO) {
        val data = cachedBackupData ?: throw IllegalStateException("No backup data cached. Call readBackup() first.")
        log(TAG) { "restoreBackup($options)" }

        var watchesImported = 0
        var watchesExisted = 0
        var checksImported = 0
        var checksExisted = 0
        var feedersImported = 0
        var feedersExisted = 0
        var statsImported = 0
        var apiKeyImported = false
        var aircraftCacheImported = 0
        var aircraftCacheExisted = 0
        val errors = mutableListOf<String>()

        // Watches
        onProgress?.invoke(BackupStep.WATCHES)
        if (options.includeWatches && data.watches != null) {
            try {
                log(TAG, INFO) { "Watch import: ${data.watches.items.size} items, ${data.watches.checks.size} checks" }
                val indexToWatchId = mutableMapOf<Int, WatchId>()
                val newWatchIndices = mutableSetOf<Int>()

                data.watches.items.forEachIndexed { index, item ->
                    try {
                        val result = importWatchItem(item)
                        indexToWatchId[index] = result.first
                        if (result.second) {
                            watchesImported++
                            newWatchIndices.add(index)
                        } else {
                            watchesExisted++
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        log(TAG, WARN) { "Failed to import watch at index $index: ${e.message}" }
                        watchesExisted++
                    }
                }

                log(TAG, INFO) { "Watch import: items done, imported=$watchesImported, existed=$watchesExisted" }

                val checkEntities = data.watches.checks
                    .filter { it.watchIndex in newWatchIndices }
                    .mapNotNull { check ->
                        val watchId = indexToWatchId[check.watchIndex] ?: return@mapNotNull null
                        WatchCheckEntity(
                            watchId = watchId,
                            checkedAt = check.checkedAt,
                            aircraftcount = check.aircraftCount,
                            seenHexes = check.seenHexes,
                        )
                    }
                watchDatabase.checks.insertAll(checkEntities)
                checksImported = checkEntities.size
                checksExisted = data.watches.checks.size - checkEntities.size
                log(TAG, INFO) { "Watch import: checks done, imported=$checksImported, existed=$checksExisted" }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log(TAG, ERROR) { "Watch restore failed: ${e.message}" }
                errors.add("Watches: ${e.message}")
            }
        }

        // Feeders
        onProgress?.invoke(BackupStep.FEEDERS)
        if (options.includeFeeders && data.feeders != null) {
            try {
                log(TAG, INFO) { "Feeder import: ${data.feeders.configs.size} configs in backup" }
                val existingGroup = feederSettings.feederGroup.value()
                val existingIds = existingGroup.configs.map { it.receiverId }.toSet()
                log(TAG, INFO) { "Feeder import: ${existingIds.size} existing feeders: $existingIds" }
                val newConfigs = data.feeders.configs.filter { it.receiverId !in existingIds }
                val newIds = newConfigs.map { it.receiverId }.toSet()
                log(TAG, INFO) { "Feeder import: ${newConfigs.size} new configs to import: $newIds" }

                if (newConfigs.isNotEmpty()) {
                    val result = feederSettings.feederGroup.update { current ->
                        log(TAG, INFO) { "Feeder import: current configs=${current.configs.size}, adding ${newConfigs.size}" }
                        current.copy(configs = current.configs + newConfigs)
                    }
                    log(TAG, INFO) { "Feeder import: update result old=${result.old.configs.size}, new=${result.new.configs.size}" }
                }

                feedersImported = newConfigs.size
                feedersExisted = data.feeders.configs.size - newConfigs.size
                log(TAG, INFO) { "Feeder import: imported=$feedersImported, skipped=$feedersExisted" }

                // Only import stats for newly added feeders
                val beastEntities = data.feeders.beastStats
                    .filter { it.receiverId in newIds }
                    .map { it.toEntity() }
                feederStatsDatabase.beastStats.insertAll(beastEntities)

                val mlatEntities = data.feeders.mlatStats
                    .filter { it.receiverId in newIds }
                    .map { it.toEntity() }
                feederStatsDatabase.mlatStats.insertAll(mlatEntities)

                statsImported = beastEntities.size + mlatEntities.size
                log(TAG, INFO) { "Feeder import: $statsImported stats imported" }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log(TAG, ERROR) { "Feeder restore failed: ${e.message}" }
                errors.add("Feeders: ${e.message}")
            }
        } else {
            log(TAG, INFO) { "Feeder import: skipped (includeFeeders=${options.includeFeeders}, hasFeeders=${data.feeders != null})" }
        }

        // Aircraft Cache
        onProgress?.invoke(BackupStep.AIRCRAFT_CACHE)
        if (options.includeAircraftCache && data.aircraftCache != null) {
            try {
                log(TAG, INFO) { "Aircraft cache import: ${data.aircraftCache.items.size} items" }
                val entities = data.aircraftCache.items.map { it.toEntity() }
                val countBefore = aircraftDatabase.count()
                aircraftDatabase.update(entities)
                val countAfter = aircraftDatabase.count()
                aircraftCacheImported = countAfter - countBefore
                aircraftCacheExisted = entities.size - aircraftCacheImported
                log(TAG, INFO) { "Aircraft cache import: imported=$aircraftCacheImported, existed=$aircraftCacheExisted" }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log(TAG, ERROR) { "Aircraft cache restore failed: ${e.message}" }
                errors.add("Aircraft cache: ${e.message}")
            }
        }

        // API Key
        onProgress?.invoke(BackupStep.API_KEY)
        if (options.includeApiKey && !data.apiKey.isNullOrBlank()) {
            try {
                generalSettings.airplanesLiveApiKey.value(data.apiKey)
                apiKeyImported = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log(TAG, ERROR) { "API key restore failed: ${e.message}" }
                errors.add("API Key: ${e.message}")
            }
        }

        RestoreResult(
            watchesImported = watchesImported,
            watchesExisted = watchesExisted,
            checksImported = checksImported,
            checksExisted = checksExisted,
            feedersImported = feedersImported,
            feedersExisted = feedersExisted,
            statsImported = statsImported,
            apiKeyImported = apiKeyImported,
            aircraftCacheImported = aircraftCacheImported,
            aircraftCacheExisted = aircraftCacheExisted,
            errors = errors,
        ).also { log(TAG, INFO) { "Restore completed: $it" } }
    }

    private suspend fun importWatchItem(item: WatchItemBackup): Pair<WatchId, Boolean> {
        val newId = makeWatchId()

        return when (item.type) {
            AircraftWatchEntity.TYPE_KEY -> {
                val hex = item.hexCode ?: throw IllegalArgumentException("Aircraft watch missing hexCode")
                if (watchDatabase.hasAircraftWatch(hex)) {
                    // Return existing watch ID for check history mapping - not worth the lookup, use newId
                    newId to false
                } else {
                    val base = BaseWatchEntity(
                        id = newId,
                        createdAt = item.createdAt,
                        watchType = AircraftWatchEntity.TYPE_KEY,
                        notificationEnabled = item.notificationEnabled,
                        userNote = item.userNote,
                    )
                    watchDatabase.importAircraft(base, AircraftWatchEntity(id = newId, hexCode = hex))
                    newId to true
                }
            }

            FlightWatchEntity.TYPE_KEY -> {
                val callsign = item.callsign ?: throw IllegalArgumentException("Flight watch missing callsign")
                if (watchDatabase.hasFlightWatch(callsign)) {
                    newId to false
                } else {
                    val base = BaseWatchEntity(
                        id = newId,
                        createdAt = item.createdAt,
                        watchType = FlightWatchEntity.TYPE_KEY,
                        notificationEnabled = item.notificationEnabled,
                        userNote = item.userNote,
                    )
                    watchDatabase.importFlight(base, FlightWatchEntity(id = newId, callsign = callsign))
                    newId to true
                }
            }

            SquawkWatchEntity.TYPE_KEY -> {
                val code = item.squawkCode ?: throw IllegalArgumentException("Squawk watch missing squawkCode")
                if (watchDatabase.hasSquawkWatch(code)) {
                    newId to false
                } else {
                    val base = BaseWatchEntity(
                        id = newId,
                        createdAt = item.createdAt,
                        watchType = SquawkWatchEntity.TYPE_KEY,
                        notificationEnabled = item.notificationEnabled,
                        userNote = item.userNote,
                    )
                    watchDatabase.importSquawk(base, SquawkWatchEntity(id = newId, code = code))
                    newId to true
                }
            }

            LocationWatchEntity.TYPE_KEY -> {
                val label = item.locationLabel ?: throw IllegalArgumentException("Location watch missing label")
                val lat = item.latitude ?: throw IllegalArgumentException("Location watch missing latitude")
                val lon = item.longitude ?: throw IllegalArgumentException("Location watch missing longitude")
                val radius = item.radiusInMeters ?: throw IllegalArgumentException("Location watch missing radius")
                if (watchDatabase.hasLocationWatch(label)) {
                    newId to false
                } else {
                    val base = BaseWatchEntity(
                        id = newId,
                        createdAt = item.createdAt,
                        watchType = LocationWatchEntity.TYPE_KEY,
                        notificationEnabled = item.notificationEnabled,
                        userNote = item.userNote,
                        latitude = lat,
                        longitude = lon,
                        radius = radius,
                    )
                    watchDatabase.importLocation(base, LocationWatchEntity(id = newId, label = label))
                    newId to true
                }
            }

            else -> throw IllegalArgumentException("Unknown watch type: ${item.type}")
        }
    }

    fun clearCache() {
        cachedBackupData = null
    }

    companion object {
        private const val BACKUP_VERSION = 1
        private const val BACKUP_ENTRY_NAME = "backup.json"
        private const val MAX_UNCOMPRESSED_SIZE = 50L * 1024 * 1024 // 50MB
        internal val TAG = logTag("Backup", "Repo")
    }
}

private fun Watch.toBackupItem(): WatchItemBackup = when (this) {
    is AircraftWatch -> WatchItemBackup(
        type = AircraftWatchEntity.TYPE_KEY,
        createdAt = addedAt,
        notificationEnabled = isNotificationEnabled,
        userNote = note,
        hexCode = hex,
    )

    is FlightWatch -> WatchItemBackup(
        type = FlightWatchEntity.TYPE_KEY,
        createdAt = addedAt,
        notificationEnabled = isNotificationEnabled,
        userNote = note,
        callsign = callsign,
    )

    is SquawkWatch -> WatchItemBackup(
        type = SquawkWatchEntity.TYPE_KEY,
        createdAt = addedAt,
        notificationEnabled = isNotificationEnabled,
        userNote = note,
        squawkCode = code,
    )

    is LocationWatch -> WatchItemBackup(
        type = LocationWatchEntity.TYPE_KEY,
        createdAt = addedAt,
        notificationEnabled = isNotificationEnabled,
        userNote = note,
        locationLabel = label,
        latitude = latitude,
        longitude = longitude,
        radiusInMeters = radiusInMeters,
    )
}

private fun BeastStatsEntity.toBackup() = BeastStatBackup(
    receiverId = receiverId,
    receivedAt = receivedAt,
    positionRate = positionRate,
    positions = positions,
    messageRate = messageRate,
    bandwidth = bandwidth,
    connectionTime = connectionTime,
    latency = latency,
)

private fun BeastStatBackup.toEntity() = BeastStatsEntity(
    receiverId = receiverId,
    receivedAt = receivedAt,
    positionRate = positionRate,
    positions = positions,
    messageRate = messageRate,
    bandwidth = bandwidth,
    connectionTime = connectionTime,
    latency = latency,
)

private fun MlatStatsEntity.toBackup() = MlatStatBackup(
    receiverId = receiverId,
    receivedAt = receivedAt,
    messageRate = messageRate,
    peerCount = peerCount,
    badSyncTimeout = badSyncTimeout,
    outlierPercent = outlierPercent,
)

private fun MlatStatBackup.toEntity() = MlatStatsEntity(
    receiverId = receiverId,
    receivedAt = receivedAt,
    messageRate = messageRate,
    peerCount = peerCount,
    badSyncTimeout = badSyncTimeout,
    outlierPercent = outlierPercent,
)

private fun CachedAircraftEntity.toAircraftCacheBackup() = AircraftCacheItemBackup(
    hex = hex,
    messageType = messageType,
    dbFlags = dbFlags,
    registration = registration,
    callsign = callsign,
    operator = operator,
    airframe = airframe,
    description = description,
    squawk = squawk,
    emergency = emergency,
    outsideTemp = outsideTemp,
    altitude = altitude,
    altitudeRate = altitudeRate,
    groundSpeed = groundSpeed,
    indicatedAirSpeed = indicatedAirSpeed,
    trackheading = trackheading,
    groundTrack = groundTrack,
    latitude = location?.latitude,
    longitude = location?.longitude,
    messages = messages,
    seenAt = seenAt,
    rssi = rssi,
)

private fun AircraftCacheItemBackup.toEntity(): CachedAircraftEntity {
    val loc = if (latitude != null && longitude != null) {
        android.location.Location("backup").apply {
            this.latitude = this@toEntity.latitude
            this.longitude = this@toEntity.longitude
        }
    } else null

    return CachedAircraftEntity(
        hex = hex,
        messageType = messageType,
        dbFlags = dbFlags,
        registration = registration,
        callsign = callsign,
        operator = operator,
        airframe = airframe,
        description = description,
        squawk = squawk,
        emergency = emergency,
        outsideTemp = outsideTemp,
        altitude = altitude,
        altitudeRate = altitudeRate,
        groundSpeed = groundSpeed,
        indicatedAirSpeed = indicatedAirSpeed,
        trackheading = trackheading,
        groundTrack = groundTrack,
        location = loc,
        messages = messages,
        seenAt = seenAt,
        rssi = rssi,
    )
}

private fun ZipInputStream.readBytesLimited(maxSize: Long): ByteArray {
    val buffer = ByteArray(8192)
    var totalRead = 0L
    val output = java.io.ByteArrayOutputStream()

    while (true) {
        val bytesRead = read(buffer)
        if (bytesRead == -1) break
        totalRead += bytesRead
        if (totalRead > maxSize) {
            throw IllegalArgumentException("Backup file exceeds maximum size of ${maxSize / 1024 / 1024}MB")
        }
        output.write(buffer, 0, bytesRead)
    }

    return output.toByteArray()
}
