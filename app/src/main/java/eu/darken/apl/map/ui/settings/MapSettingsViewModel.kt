package eu.darken.apl.map.ui.settings

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.datastore.valueBlocking
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.uix.ViewModel4
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
        mapSettings.nativeMapStyle.flow,
        mapSettings.isRestoreLastViewEnabled.flow,
    ) { style, restoreLastView ->
        State(
            nativeMapStyle = NativeMapStyle.fromKey(style),
            isRestoreLastViewEnabled = restoreLastView,
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

    data class State(
        val nativeMapStyle: NativeMapStyle,
        val isRestoreLastViewEnabled: Boolean,
    )
}
