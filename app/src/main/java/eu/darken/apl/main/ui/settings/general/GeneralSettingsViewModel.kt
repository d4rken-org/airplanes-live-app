package eu.darken.apl.main.ui.settings.general

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.apl.common.BuildConfigWrap
import eu.darken.apl.common.WebpageTool
import eu.darken.apl.common.coroutine.AppScope
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.datastore.value
import eu.darken.apl.common.datastore.valueBlocking
import eu.darken.apl.common.debug.autoreport.DebugSettings
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.flow.combine
import eu.darken.apl.common.theming.ThemeColor
import eu.darken.apl.common.theming.ThemeMode
import eu.darken.apl.common.theming.ThemePalette
import eu.darken.apl.common.theming.ThemeStyle
import eu.darken.apl.common.uix.ViewModel4
import eu.darken.apl.main.core.GeneralSettings
import eu.darken.apl.upgrade.UpgradeRepo
import eu.darken.apl.upgrade.ui.DestinationUpgrade
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

@HiltViewModel
class GeneralSettingsViewModel @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    private val dispatcherProvider: DispatcherProvider,
    private val generalSettings: GeneralSettings,
    private val webpageTool: WebpageTool,
    private val debugSettings: DebugSettings,
    @AppScope private val appScope: CoroutineScope,
    upgradeRepo: UpgradeRepo,
) : ViewModel4(
    dispatcherProvider = dispatcherProvider,
    tag = logTag("Settings", "General", "VM"),
) {

    val state = combine(
        generalSettings.themeMode.flow,
        generalSettings.themeStyle.flow,
        generalSettings.themeColor.flow,
        generalSettings.themeCustomSeed.flow,
        generalSettings.themeCustomPalette.flow,
        generalSettings.isUpdateCheckEnabled.flow,
        flowOf(BuildConfigWrap.FLAVOR == BuildConfigWrap.Flavor.FOSS),
        upgradeRepo.upgradeInfo,
        flowOf(BuildConfigWrap.BUILD_TYPE == BuildConfigWrap.BuildType.DEV),
        debugSettings.isFakeUpgradeEnabled.flow,
    ) { themeMode, themeStyle, themeColor, customSeed, customPalette, isUpdateCheckEnabled, isUpdateCheckSupported, upgrade,
        isDebugBuild, isFakeUpgradeEnabled ->
        State(
            themeMode = themeMode,
            themeStyle = themeStyle,
            themeColor = themeColor,
            customSeed = customSeed,
            customPalette = customPalette,
            isUpdateCheckEnabled = isUpdateCheckEnabled,
            isUpdateCheckSupported = isUpdateCheckSupported,
            isUpgraded = upgrade.isSettled && upgrade.isPro,
            isDebugBuild = isDebugBuild,
            isFakeUpgradeEnabled = isFakeUpgradeEnabled,
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

    // The delayed writes run in the app scope, so navigating away before the delay ends keeps them
    fun setThemeColor(color: ThemeColor, delayMs: Long = 0) = launch(scope = appScope) {
        log(tag) { "setThemeColor($color, delayMs=$delayMs)" }
        delay(delayMs)
        generalSettings.themeColor.value(color)
    }

    fun applyCustomTheme(seed: Int?, palette: ThemePalette, delayMs: Long = 0) = launch(scope = appScope) {
        log(tag) { "applyCustomTheme($seed, $palette, delayMs=$delayMs)" }
        delay(delayMs)
        generalSettings.applyCustomTheme(seed, palette)
    }

    fun goUpgrade() = navTo(DestinationUpgrade)

    fun toggleUpdateCheck() {
        log(tag) { "toggleUpdateCheck()" }
        generalSettings.isUpdateCheckEnabled.valueBlocking = !generalSettings.isUpdateCheckEnabled.valueBlocking
    }

    fun toggleFakeUpgrade() {
        log(tag) { "toggleFakeUpgrade()" }
        debugSettings.isFakeUpgradeEnabled.valueBlocking = !debugSettings.isFakeUpgradeEnabled.valueBlocking
    }

    data class State(
        val themeMode: ThemeMode,
        val themeStyle: ThemeStyle,
        val themeColor: ThemeColor,
        val customSeed: Int?,
        val customPalette: ThemePalette,
        val isUpdateCheckEnabled: Boolean,
        val isUpdateCheckSupported: Boolean,
        val isUpgraded: Boolean,
        val isDebugBuild: Boolean,
        val isFakeUpgradeEnabled: Boolean,
    )
}
