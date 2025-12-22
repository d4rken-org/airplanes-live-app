package eu.darken.apl.map.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.apl.common.datastore.PreferenceScreenData
import eu.darken.apl.common.datastore.PreferenceStoreMapper
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
) : PreferenceScreenData {

    private val Context.dataStore by preferencesDataStore(name = "settings_map")

    override val dataStore: DataStore<Preferences>
        get() = context.dataStore

    val isRestoreLastViewEnabled = dataStore.createValue("map.restore.last.view.enabled", true)
    val lastCamera = dataStore.createJsonValue<SavedCamera?>("map.last.camera", null, json)

    override val mapper = PreferenceStoreMapper(
        isRestoreLastViewEnabled,
    )

    companion object {
        internal val TAG = logTag("Map", "Settings")
    }
}
