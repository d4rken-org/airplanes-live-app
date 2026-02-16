package eu.darken.apl.main.ui.settings.general

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.apl.common.BuildConfigWrap
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.datastore.value
import eu.darken.apl.common.datastore.valueBlocking
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.theming.ThemeMode
import eu.darken.apl.common.theming.ThemeStyle
import eu.darken.apl.common.uix.ViewModel4
import eu.darken.apl.main.core.GeneralSettings
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

@HiltViewModel
class GeneralSettingsViewModel @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val generalSettings: GeneralSettings,
) : ViewModel4(
    dispatcherProvider = dispatcherProvider,
    tag = logTag("Settings", "General", "VM"),
) {

    val state = combine(
        generalSettings.themeMode.flow,
        generalSettings.themeStyle.flow,
        generalSettings.isUpdateCheckEnabled.flow,
        flowOf(BuildConfigWrap.FLAVOR == BuildConfigWrap.Flavor.FOSS),
    ) { themeMode, themeStyle, isUpdateCheckEnabled, isUpdateCheckSupported ->
        State(
            themeMode = themeMode,
            themeStyle = themeStyle,
            isUpdateCheckEnabled = isUpdateCheckEnabled,
            isUpdateCheckSupported = isUpdateCheckSupported,
        )
    }.asStateFlow()

    fun setThemeMode(mode: ThemeMode) = launch {
        log(tag) { "setThemeMode($mode)" }
        generalSettings.themeMode.value(mode)
    }

    fun setThemeStyle(style: ThemeStyle) = launch {
        log(tag) { "setThemeStyle($style)" }
        generalSettings.themeStyle.value(style)
    }

    fun toggleUpdateCheck() {
        log(tag) { "toggleUpdateCheck()" }
        generalSettings.isUpdateCheckEnabled.valueBlocking = !generalSettings.isUpdateCheckEnabled.valueBlocking
    }

    data class State(
        val themeMode: ThemeMode,
        val themeStyle: ThemeStyle,
        val isUpdateCheckEnabled: Boolean,
        val isUpdateCheckSupported: Boolean,
    )
}
