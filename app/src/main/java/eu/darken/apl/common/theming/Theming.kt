package eu.darken.apl.common.theming

import android.app.Activity
import androidx.appcompat.app.AppCompatDelegate
import eu.darken.apl.common.coroutine.AppScope
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.Logging.Priority.INFO
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.flow.setupCommonEventHandlers
import eu.darken.apl.main.core.GeneralSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Theming @Inject constructor(
    @param:AppScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val generalSettings: GeneralSettings,
) {

    fun setup() {
        log(TAG) { "setup()" }

        generalSettings.themeMode.flow
            .onEach { mode ->
                log(TAG) { "ThemeMode changed: $mode" }
                withContext(dispatcherProvider.Main) {
                    mode.applyMode()
                }
            }
            .setupCommonEventHandlers(TAG) { "themeMode" }
            .launchIn(appScope)
    }

    private fun ThemeMode.applyMode() = when (this) {
        ThemeMode.SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        ThemeMode.DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        ThemeMode.LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
    }

    fun notifySplashScreenDone(activity: Activity) {
        log(TAG, INFO) { "notifySplashScreenDone($activity)" }
    }

    companion object {
        private val TAG = logTag("UI", "Theming")
    }
}
