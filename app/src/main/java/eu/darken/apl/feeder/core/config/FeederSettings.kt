package eu.darken.apl.feeder.core.config

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.feeder.core.FeederCache
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import eu.darken.apl.common.datastore.createValue as createJsonValue

@Singleton
class FeederSettings @Inject constructor(
    @param:ApplicationContext private val context: Context,
    json: Json,
) {

    private val Context.dataStore by preferencesDataStore(name = "settings_feeder")

    /** Account-owned feeders, cached for instant display + offline-monitoring comparison. */
    val feederCache = context.dataStore.createJsonValue(
        "feeder.cache",
        FeederCache(),
        json,
        onErrorFallbackToDefault = true,
    )

    // New key (v2): the previous "feeder.sort.mode" could hold the removed BY_MESSAGE_RATE value.
    val feederSortMode = context.dataStore.createJsonValue(
        "feeder.sort.mode.v2",
        FeederSortMode.BY_LABEL,
        json,
        onErrorFallbackToDefault = true,
    )

    val feederMonitorInterval =
        context.dataStore.createJsonValue("feeder.monitor.interval", DEFAULT_CHECK_INTERVAL, json)

    val lastRefreshAt = context.dataStore.createJsonValue("feeder.refresh.last", Instant.EPOCH, json)

    /**
     * Per-feeder notification mute — user intent, kept apart from the monitor's own state so a
     * monitor bug can never silently un-mute. Cleared when a logged-out monitor cycle runs;
     * feeder IDs are globally unique UUIDs, so stale entries can't affect another account.
     */
    val mutedFeeders = context.dataStore.createJsonValue(
        "feeder.muted",
        emptySet<String>(),
        json,
        onErrorFallbackToDefault = true,
    )

    /** Written only by [eu.darken.apl.feeder.core.monitor.FeederMonitor]. */
    val monitorState = context.dataStore.createJsonValue(
        "feeder.monitor.state",
        eu.darken.apl.feeder.core.monitor.FeederMonitorState(),
        json,
        onErrorFallbackToDefault = true,
    )

    /** Opt-in: device-health warnings require one detail API call per feeder per cycle. */
    val deviceHealthWarningsEnabled =
        context.dataStore.createJsonValue("feeder.health.warnings.enabled", false, json)

    companion object {
        val DEFAULT_CHECK_INTERVAL = Duration.ofMinutes(60)
        internal val TAG = logTag("Feeder", "Settings")
    }
}
