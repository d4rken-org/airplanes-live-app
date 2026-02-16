package eu.darken.apl.feeder.core.config

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.apl.common.debug.logging.logTag
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

    val feederGroup = context.dataStore.createJsonValue("feeder.group", FeederGroup(), json)
    val feederSortMode = context.dataStore.createJsonValue("feeder.sort.mode", FeederSortMode.BY_LABEL, json)

    val feederMonitorInterval =
        context.dataStore.createJsonValue("feeder.monitor.interval", DEFAULT_CHECK_INTERVAL, json)
    val lastUpdate = context.dataStore.createJsonValue("feeder.update.last", Instant.EPOCH, json)

    companion object {
        val DEFAULT_CHECK_INTERVAL = Duration.ofMinutes(60)
        internal val TAG = logTag("Feeder", "Settings")
    }
}
