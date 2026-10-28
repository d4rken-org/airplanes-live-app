package eu.darken.apl.map.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.apl.common.datastore.createValue
import eu.darken.apl.common.debug.logging.logTag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import eu.darken.apl.common.datastore.createValue as createJsonValue

private val Context.settingsMapDataStore by preferencesDataStore(name = "settings_map")

@Singleton
class MapSettings internal constructor(
    private val dataStore: DataStore<Preferences>,
    json: Json,
) {

    @Inject constructor(
        @ApplicationContext context: Context,
        json: Json,
    ) : this(context.settingsMapDataStore, json)

    val nativeMapStyle = dataStore.createValue("map.native.style", NativeMapStyle.AUTO.key)
    val isAllTracksEnabled = dataStore.createValue("map.native.tracks.all.enabled", false)
    val isLabelsEnabled = dataStore.createValue("map.native.labels.enabled", true)
    val isMilitaryOnlyEnabled = dataStore.createValue("map.native.military.only.enabled", false)
    val isMilitaryHighlightEnabled = dataStore.createValue("map.native.military.highlight.enabled", true)
    val isWatchHighlightEnabled = dataStore.createValue("map.native.watch.highlight.enabled", true)
    val isRestoreLastViewEnabled = dataStore.createValue("map.restore.last.view.enabled", true)
    val lastCamera = dataStore.createJsonValue<SavedCamera?>("map.last.camera", null, json)

    val labelFields: Flow<MapLabelFields> = dataStore.data.map { it.labelFields() }

    /** Returns false if nothing changed, the last enabled field can't be turned off. */
    suspend fun setLabelField(field: MapLabelField, enabled: Boolean): Boolean {
        var changed = false
        dataStore.edit { prefs ->
            val current = prefs.labelFields()
            if (current.isEnabled(field) == enabled) return@edit
            if (!enabled && !current.canDisable(field)) return@edit
            val updated = current.with(field, enabled)
            MapLabelField.entries.forEach { prefs[it.key] = updated.isEnabled(it) }
            changed = true
        }
        return changed
    }

    private fun Preferences.labelFields(): MapLabelFields {
        val defaults = MapLabelFields()
        return MapLabelFields.orDefaults(
            MapLabelFields(
                callsign = this[MapLabelField.CALLSIGN.key] ?: defaults.callsign,
                type = this[MapLabelField.TYPE.key] ?: defaults.type,
                altitude = this[MapLabelField.ALTITUDE.key] ?: defaults.altitude,
                speed = this[MapLabelField.SPEED.key] ?: defaults.speed,
            )
        )
    }

    private val MapLabelField.key: Preferences.Key<Boolean>
        get() = when (this) {
            MapLabelField.CALLSIGN -> KEY_LABEL_CALLSIGN
            MapLabelField.TYPE -> KEY_LABEL_TYPE
            MapLabelField.ALTITUDE -> KEY_LABEL_ALTITUDE
            MapLabelField.SPEED -> KEY_LABEL_SPEED
        }

    companion object {
        internal val TAG = logTag("Map", "Settings")

        private val KEY_LABEL_CALLSIGN = booleanPreferencesKey("map.native.labels.callsign")
        private val KEY_LABEL_TYPE = booleanPreferencesKey("map.native.labels.type")
        private val KEY_LABEL_ALTITUDE = booleanPreferencesKey("map.native.labels.altitude")
        private val KEY_LABEL_SPEED = booleanPreferencesKey("map.native.labels.speed")
    }
}
