package eu.darken.apl.feeder.core.config

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.apl.common.datastore.createValue
import eu.darken.apl.common.debug.logging.logTag
import kotlinx.serialization.json.Json
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
    val isNavTabVisible = context.dataStore.createValue("feeder.navtab.visible", true)
    val notifyProblems = context.dataStore.createValue("feeder.notify.problems", true)

    /** Keys left behind by app versions that checked the feeders themselves. */
    suspend fun removeLegacyMonitorKeys() {
        context.dataStore.edit { prefs ->
            LEGACY_MONITOR_KEYS.forEach { prefs.remove(stringPreferencesKey(it)) }
        }
    }

    companion object {
        private val LEGACY_MONITOR_KEYS = listOf("feeder.observations", "feeder.cleanup.last")
        internal val TAG = logTag("Feeder", "Settings")
    }
}
