package eu.darken.apl.watch.ui.create

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.uix.ViewModel4
import eu.darken.apl.main.core.aircraft.AircraftHex
import eu.darken.apl.upgrade.ui.DestinationUpgrade
import eu.darken.apl.watch.core.WatchLimit
import eu.darken.apl.watch.core.WatchRepo
import javax.inject.Inject

@HiltViewModel
class CreateAircraftWatchViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val watchRepo: WatchRepo,
) : ViewModel4(
    dispatcherProvider = dispatcherProvider,
    tag = logTag("Aircraft", "Create", "VM"),
) {

    val limit = watchRepo.limit.asStateFlow(defaultValue = WatchLimit(count = 0, max = null))

    fun create(hex: AircraftHex, note: String) = launch {
        log(tag) { "create($hex, $note)" }
        watchRepo.createAircraft(hex.uppercase(), note.trim())
        navUp()
    }

    fun goUpgrade() = navTo(DestinationUpgrade)
}
