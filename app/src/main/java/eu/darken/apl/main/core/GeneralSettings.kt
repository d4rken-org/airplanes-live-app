package eu.darken.apl.main.core

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.apl.common.datastore.DataStoreValue
import eu.darken.apl.common.datastore.createValue
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.theming.ThemeColor
import eu.darken.apl.common.theming.ThemeMode
import eu.darken.apl.common.theming.ThemePalette
import eu.darken.apl.common.theming.ThemeStyle
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import eu.darken.apl.common.datastore.createValue as createJsonValue

private val Context.settingsCoreDataStore by preferencesDataStore(name = "settings_core")

@Singleton
class GeneralSettings internal constructor(
    private val dataStore: DataStore<Preferences>,
    json: Json,
) {

    @Inject constructor(
        @ApplicationContext context: Context,
        json: Json,
    ) : this(context.settingsCoreDataStore, json)

    val deviceLabel = dataStore.createValue("core.device.label", Build.DEVICE)

    val isAutoReportingEnabled = dataStore.createValue("debug.bugreport.automatic.enabled", true)
    val isUpdateCheckEnabled = dataStore.createValue("updater.check.enabled", false)
    val dismissedUpdateVersion = dataStore.createValue<String?>("updater.dismissed.version", null)

    val isOnboardingFinished = dataStore.createValue("core.onboarding.finished", false)

    val themeMode = dataStore.createJsonValue("core.ui.theme.mode", ThemeMode.SYSTEM, json, onErrorFallbackToDefault = true)
    val themeStyle = dataStore.createJsonValue("core.ui.theme.style", ThemeStyle.DEFAULT, json, onErrorFallbackToDefault = true)
    val themeColor = dataStore.createJsonValue("core.ui.theme.color", ThemeColor.BLUE, json, onErrorFallbackToDefault = true)

    /** An RGB seed without alpha. Null uses [ThemeState.DEFAULT_CUSTOM_SEED]. */
    val themeCustomSeed = dataStore.createValue<Int?>("core.ui.theme.custom.seed", null)
    val themeCustomPalette = dataStore.createJsonValue("core.ui.theme.custom.palette", ThemePalette.TONAL_SPOT, json, onErrorFallbackToDefault = true)

    val searchLocationDismissed = dataStore.createValue("search.location.dismissed", false)

    /** Seed and palette change together, so no reader sees one without the other. */
    suspend fun setThemeCustom(seed: Int?, palette: ThemePalette) {
        dataStore.edit { prefs ->
            prefs.write(themeCustomSeed, seed)
            prefs.write(themeCustomPalette, palette)
        }
    }

    private fun <T> MutablePreferences.write(value: DataStoreValue<T>, newValue: T) {
        @Suppress("UNCHECKED_CAST")
        val key = value.key as Preferences.Key<Any>
        val raw = value.writer(newValue)
        if (raw == null) remove(key) else set(key, raw)
    }

    companion object {
        internal val TAG = logTag("Core", "Settings")
    }
}
