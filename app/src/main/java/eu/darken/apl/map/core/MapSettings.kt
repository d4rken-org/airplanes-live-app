package eu.darken.apl.map.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.apl.common.datastore.createValue
import eu.darken.apl.common.debug.logging.logTag
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import eu.darken.apl.common.datastore.createValue as createJsonValue

@Singleton
class MapSettings @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val json: Json,
) {

    private val Context.dataStore by preferencesDataStore(name = "settings_map")

    val isRestoreLastViewEnabled = context.dataStore.createValue("map.restore.last.view.enabled", true)
    val isNativeInfoPanelEnabled = context.dataStore.createValue("map.native.info.panel.enabled", true)
    val lastCamera = context.dataStore.createJsonValue<SavedCamera?>("map.last.camera", null, json)

    companion object {
        internal val TAG = logTag("Map", "Settings")
    }
}
