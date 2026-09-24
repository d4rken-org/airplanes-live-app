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
import eu.darken.apl.map.core.NativeMapStyle
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
        combine(
            mapSettings.isLegacyMapEnabled.flow,
            mapSettings.nativeMapStyle.flow,
            mapSettings.isRestoreLastViewEnabled.flow,
        ) { legacy, style, restore -> Triple(legacy, NativeMapStyle.fromKey(style), restore) },
        mapSettings.isNativeInfoPanelEnabled.flow,
        mapSettings.isHoverInfoEnabled.flow,
        mapSettings.mapLayer.flow,
        mapSettings.enabledOverlays.flow,
    ) { (legacy, style, restoreLastView), nativeInfoPanel, hoverInfo, layerKey, overlayKeys ->
        State(
            isLegacyMapEnabled = legacy,
            nativeMapStyle = style,
            isRestoreLastViewEnabled = restoreLastView,
            isNativeInfoPanelEnabled = nativeInfoPanel,
            isHoverInfoEnabled = hoverInfo,
            mapLayer = MapLayer.fromKey(layerKey),
            enabledOverlays = overlayKeys ?: emptySet(),
        )
    }.asStateFlow()

    fun toggleLegacyMap() {
        log(tag) { "toggleLegacyMap()" }
        mapSettings.isLegacyMapEnabled.valueBlocking = !mapSettings.isLegacyMapEnabled.valueBlocking
    }

    fun setNativeMapStyle(style: NativeMapStyle) {
        log(tag) { "setNativeMapStyle($style)" }
        mapSettings.nativeMapStyle.valueBlocking = style.key
    }

    fun toggleRestoreLastView() {
        log(tag) { "toggleRestoreLastView()" }
        mapSettings.isRestoreLastViewEnabled.valueBlocking = !mapSettings.isRestoreLastViewEnabled.valueBlocking
    }

    fun toggleNativeInfoPanel() {
        log(tag) { "toggleNativeInfoPanel()" }
        mapSettings.isNativeInfoPanelEnabled.valueBlocking = !mapSettings.isNativeInfoPanelEnabled.valueBlocking
    }

    fun toggleHoverInfo() {
        log(tag) { "toggleHoverInfo()" }
        mapSettings.isHoverInfoEnabled.valueBlocking = !mapSettings.isHoverInfoEnabled.valueBlocking
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
        val isLegacyMapEnabled: Boolean,
        val nativeMapStyle: NativeMapStyle,
        val isRestoreLastViewEnabled: Boolean,
        val isNativeInfoPanelEnabled: Boolean,
        val isHoverInfoEnabled: Boolean,
        val mapLayer: MapLayer,
        val enabledOverlays: Set<String>,
    )
}
