package eu.darken.apl.map.ui.settings

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.datastore.valueBlocking
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.uix.ViewModel4
import eu.darken.apl.map.core.MapSettings
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

@HiltViewModel
class MapSettingsViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val mapSettings: MapSettings,
) : ViewModel4(
    dispatcherProvider = dispatcherProvider,
    tag = logTag("Settings", "Map", "VM"),
) {

    val state = combine(
        mapSettings.isRestoreLastViewEnabled.flow,
        mapSettings.isNativeInfoPanelEnabled.flow,
    ) { restoreLastView, nativeInfoPanel ->
        State(
            isRestoreLastViewEnabled = restoreLastView,
            isNativeInfoPanelEnabled = nativeInfoPanel,
        )
    }.asStateFlow()

    fun toggleRestoreLastView() {
        log(tag) { "toggleRestoreLastView()" }
        mapSettings.isRestoreLastViewEnabled.valueBlocking = !mapSettings.isRestoreLastViewEnabled.valueBlocking
    }

    fun toggleNativeInfoPanel() {
        log(tag) { "toggleNativeInfoPanel()" }
        mapSettings.isNativeInfoPanelEnabled.valueBlocking = !mapSettings.isNativeInfoPanelEnabled.valueBlocking
    }

    data class State(
        val isRestoreLastViewEnabled: Boolean,
        val isNativeInfoPanelEnabled: Boolean,
    )
}
