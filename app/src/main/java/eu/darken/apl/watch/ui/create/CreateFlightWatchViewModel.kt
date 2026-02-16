package eu.darken.apl.watch.ui.create

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.uix.ViewModel4
import eu.darken.apl.main.core.aircraft.Callsign
import eu.darken.apl.watch.core.WatchRepo
import javax.inject.Inject

@HiltViewModel
class CreateFlightWatchViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val watchRepo: WatchRepo,
) : ViewModel4(
    dispatcherProvider = dispatcherProvider,
    tag = logTag("Flight", "Create", "VM"),
) {

    fun create(callsign: Callsign, note: String) = launch {
        log(tag) { "create($callsign, $note)" }
        watchRepo.createFlight(callsign, note.trim())
        navUp()
    }
}
