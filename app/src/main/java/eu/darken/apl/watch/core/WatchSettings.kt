package eu.darken.apl.watch.core

// Import the specific createValue function from DataStoreValueJson
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.apl.common.debug.logging.logTag
import kotlinx.serialization.json.Json
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton
import eu.darken.apl.common.datastore.createValue as createJsonValue

@Singleton
class WatchSettings @Inject constructor(
    @param:ApplicationContext private val context: Context,
    json: Json,
) {

    private val Context.dataStore by preferencesDataStore(name = "settings_alerts")

    val watchSortMode = context.dataStore.createJsonValue("watch.sort.mode", WatchSortMode.BY_NOTE, json, onErrorFallbackToDefault = true)

    val lastCleanup = context.dataStore.createJsonValue("watch.cleanup.last", java.time.Instant.EPOCH, json)

    val lastCheck = context.dataStore.createJsonValue("watch.check.last", java.time.Instant.EPOCH, json)

    companion object {
        val FOREGROUND_CHECK_MAX_AGE: Duration = Duration.ofMinutes(15)
        val PRO_FOREGROUND_CHECK_INTERVAL: Duration = Duration.ofSeconds(60)
        internal val TAG = logTag("Watch", "Settings")
    }
}
