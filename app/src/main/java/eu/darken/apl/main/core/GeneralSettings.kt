package eu.darken.apl.main.core

import android.content.Context
import android.os.Build
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.apl.common.datastore.createValue
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.theming.ThemeColor
import eu.darken.apl.common.theming.ThemeMode
import eu.darken.apl.common.theming.ThemeStyle
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
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
    val dismissedUpdateVersion = context.dataStore.createValue<String?>("updater.dismissed.version", null)

    val isOnboardingFinished = context.dataStore.createValue("core.onboarding.finished", false)

    val themeMode = context.dataStore.createJsonValue("core.ui.theme.mode", ThemeMode.SYSTEM, json, onErrorFallbackToDefault = true)
    val themeStyle = context.dataStore.createJsonValue("core.ui.theme.style", ThemeStyle.DEFAULT, json, onErrorFallbackToDefault = true)
    val themeColor = context.dataStore.createJsonValue("core.ui.theme.color", ThemeColor.BLUE, json, onErrorFallbackToDefault = true)

    val searchLocationDismissed = context.dataStore.createValue("search.location.dismissed", false)

    companion object {
        internal val TAG = logTag("Core", "Settings")
    }
}
