package eu.darken.apl.watch.ui.create

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.uix.ViewModel4
import eu.darken.apl.main.core.aircraft.SquawkCode
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.watch.core.WatchRepo
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class CreateSquawkWatchViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val watchRepo: WatchRepo,
    accessRepo: AccessRepo,
) : ViewModel4(
    dispatcherProvider = dispatcherProvider,
    tag = logTag("Squawk", "Create", "VM"),
) {

    val isAllowed = accessRepo.state
        .map { it == null || it.allowsWatchType(WATCH_TYPE) }
        .asStateFlow(defaultValue = true)

    fun create(code: SquawkCode, note: String) = launch {
        log(tag) { "create($code, $note)" }
        watchRepo.createSquawk(code, note.trim())
        navUp()
    }

    companion object {
        private const val WATCH_TYPE = "squawk"
    }
}
