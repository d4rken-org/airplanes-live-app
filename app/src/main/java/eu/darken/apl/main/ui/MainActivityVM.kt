package eu.darken.apl.main.ui

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.apl.common.SponsorHelper
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.uix.ViewModel4
import eu.darken.apl.main.core.GeneralSettings
import eu.darken.apl.main.core.ThemeState
import eu.darken.apl.main.core.themeState
import eu.darken.apl.map.core.MapOptions
import eu.darken.apl.map.ui.DestinationMap
import eu.darken.apl.watch.core.WatchId
import eu.darken.apl.watch.core.WatchRepo
import eu.darken.apl.watch.core.getStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject


@HiltViewModel
class MainActivityVM @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val sponsorHelper: SponsorHelper,
    private val watchRepo: WatchRepo,
    generalSettings: GeneralSettings,
) : ViewModel4(
    dispatcherProvider = dispatcherProvider,
    tag = logTag("Main", "Activity", "ViewModel")
) {

    private val readyStateInternal = MutableStateFlow(true)
    val readyState = readyStateInternal.asStateFlow()

    val themeState = generalSettings.themeState
        .stateIn(vmScope, SharingStarted.Eagerly, ThemeState())

    fun onGo() {
        // Ready
    }

    fun goSponsor() = launch {
        sponsorHelper.openSponsorPage()
    }

    fun showWatchAlert(watchId: WatchId) = launch {
        val status = watchRepo.getStatus(watchId)
        if (status == null) {
            log(TAG, WARN) { "Watch with id $watchId no longer exists" }
            return@launch
        }
        if (status.tracked.isEmpty()) {
            log(TAG) { "No aircraft: $status" }
        } else {
            val mapOptions = MapOptions.focus(status.tracked.map { it.hex })
            navTo(DestinationMap(mapOptions = mapOptions))
        }
    }

    companion object {
        private val TAG = logTag("Main", "Activity", "ViewModel")
    }
}
