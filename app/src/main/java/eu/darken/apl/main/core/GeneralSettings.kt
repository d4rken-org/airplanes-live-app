package eu.darken.apl.main.core

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.apl.common.datastore.createValue
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.theming.ThemeMode
import eu.darken.apl.common.theming.ThemeStyle
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
// Import the specific createValue function from DataStoreValueJson
import eu.darken.apl.common.datastore.createValue as createJsonValue

@Singleton
class GeneralSettings @Inject constructor(
    @param:ApplicationContext private val context: Context,
    json: Json,
) {

    private val Context.dataStore by preferencesDataStore(name = "settings_core")

    val deviceLabel = context.dataStore.createValue("core.device.label", Build.DEVICE)

    val isAutoReportingEnabled = context.dataStore.createValue("debug.bugreport.automatic.enabled", true)
    val isUpdateCheckEnabled = context.dataStore.createValue("updater.check.enabled", false)

    val isOnboardingFinished = context.dataStore.createValue("core.onboarding.finished", false)

    val themeMode = context.dataStore.createJsonValue("core.ui.theme.mode", ThemeMode.SYSTEM, json)
    val themeStyle = context.dataStore.createJsonValue("core.ui.theme.style", ThemeStyle.DEFAULT, json)

    val searchLocationDismissed = context.dataStore.createValue("search.location.dismissed", false)

    companion object {
        internal val TAG = logTag("Core", "Settings")
    }
}
