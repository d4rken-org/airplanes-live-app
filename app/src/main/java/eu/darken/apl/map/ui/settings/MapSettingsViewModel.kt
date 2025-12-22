package eu.darken.apl.map.ui.settings

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.uix.ViewModel3
import javax.inject.Inject

@HiltViewModel
class MapSettingsViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
) : ViewModel3(
    dispatcherProvider,
    tag = logTag("Settings", "Map", "VM"),
)
