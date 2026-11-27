package eu.darken.apl.map.core

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import eu.darken.apl.map.core.MapSettings.Companion.KEY_LABELS_ENABLED
import eu.darken.apl.map.core.MapSettings.Companion.KEY_LABELS_MIGRATED
import eu.darken.apl.map.core.MapSettings.Companion.key

/**
 * New installs start without labels, existing installs keep the labels they showed:
 * a switched off "Show labels" becomes all fields off, never chosen fields become callsign only.
 */
internal class MapLabelMigration : DataMigration<Preferences> {

    override suspend fun shouldMigrate(currentData: Preferences): Boolean = currentData[KEY_LABELS_MIGRATED] == null

    override suspend fun migrate(currentData: Preferences): Preferences {
        val prefs = currentData.toMutablePreferences()
        val existingInstall = currentData.asMap().keys.any { it != KEY_LABELS_MIGRATED }
        if (existingInstall) {
            val labelsOn = currentData[KEY_LABELS_ENABLED] ?: true
            val fieldsStored = MapLabelField.entries.any { currentData[it.key] != null }
            when {
                !labelsOn -> MapLabelField.entries.forEach { prefs[it.key] = false }
                !fieldsStored -> MapLabelField.entries.forEach { prefs[it.key] = it == MapLabelField.CALLSIGN }
            }
            prefs.remove(KEY_LABELS_ENABLED)
        }
        prefs[KEY_LABELS_MIGRATED] = true
        return prefs.toPreferences()
    }

    override suspend fun cleanUp() = Unit
}
