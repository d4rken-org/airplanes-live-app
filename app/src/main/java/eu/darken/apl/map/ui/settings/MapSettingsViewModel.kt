package eu.darken.apl.map.ui.settings

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.datastore.valueBlocking
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.uix.ViewModel4
import eu.darken.apl.map.core.MapSettings
import eu.darken.apl.map.core.NativeMapStyle
import eu.darken.apl.upgrade.UpgradeRepo
import eu.darken.apl.upgrade.ui.DestinationUpgrade
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

@HiltViewModel
class MapSettingsViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val mapSettings: MapSettings,
    upgradeRepo: UpgradeRepo,
) : ViewModel4(
    dispatcherProvider = dispatcherProvider,
    tag = logTag("Settings", "Map", "VM"),
) {

    val state = combine(
        mapSettings.nativeMapStyle.flow,
        mapSettings.isRestoreLastViewEnabled.flow,
        upgradeRepo.upgradeInfo,
    ) { style, restoreLastView, upgrade ->
        State(
            nativeMapStyle = NativeMapStyle.fromKey(style),
            isRestoreLastViewEnabled = restoreLastView,
            isUpgraded = upgrade.isSettled && upgrade.isPro,
        )
    }.asStateFlow()

    fun setNativeMapStyle(style: NativeMapStyle) {
        log(tag) { "setNativeMapStyle($style)" }
        mapSettings.nativeMapStyle.valueBlocking = style.key
    }

    fun toggleRestoreLastView() {
        log(tag) { "toggleRestoreLastView()" }
        mapSettings.isRestoreLastViewEnabled.valueBlocking = !mapSettings.isRestoreLastViewEnabled.valueBlocking
    }

    fun goUpgrade() = navTo(DestinationUpgrade)

    data class State(
        val nativeMapStyle: NativeMapStyle,
        val isRestoreLastViewEnabled: Boolean,
        val isUpgraded: Boolean,
    )
}
