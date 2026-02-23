package eu.darken.apl.map.ui.settings

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.datastore.valueBlocking
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.uix.ViewModel4
import eu.darken.apl.map.core.MapLayer
import eu.darken.apl.map.core.MapOverlay
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
        mapSettings.mapLayer.flow,
        mapSettings.enabledOverlays.flow,
    ) { restoreLastView, nativeInfoPanel, layerKey, overlayKeys ->
        State(
            isRestoreLastViewEnabled = restoreLastView,
            isNativeInfoPanelEnabled = nativeInfoPanel,
            mapLayer = MapLayer.fromKey(layerKey),
            enabledOverlays = overlayKeys ?: emptySet(),
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

    fun setMapLayer(layer: MapLayer) {
        log(tag) { "setMapLayer($layer)" }
        mapSettings.mapLayer.valueBlocking = layer.key
    }

    fun toggleOverlay(overlay: MapOverlay) {
        log(tag) { "toggleOverlay($overlay)" }
        val current = mapSettings.enabledOverlays.valueBlocking ?: emptySet()
        val updated = if (overlay.key in current) current - overlay.key else current + overlay.key
        mapSettings.enabledOverlays.valueBlocking = updated.ifEmpty { null }
    }

    data class State(
        val isRestoreLastViewEnabled: Boolean,
        val isNativeInfoPanelEnabled: Boolean,
        val mapLayer: MapLayer,
        val enabledOverlays: Set<String>,
    )
}
