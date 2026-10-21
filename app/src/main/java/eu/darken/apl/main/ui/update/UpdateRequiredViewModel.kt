package eu.darken.apl.main.ui.update

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.uix.ViewModel4
import eu.darken.apl.main.core.update.AppUpdateTarget
import eu.darken.apl.server.compat.AppCompatibility
import eu.darken.apl.server.compat.AppCompatibilityCheck
import eu.darken.apl.server.compat.CompatState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Backs [rememberUpdateRequiredNotice] on the screens that need the server. */
@HiltViewModel
class UpdateRequiredViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    compatibility: AppCompatibility,
    private val compatibilityCheck: AppCompatibilityCheck,
    private val updateTarget: AppUpdateTarget,
) : ViewModel4(
    dispatcherProvider = dispatcherProvider,
    tag = logTag("Update", "Required", "ViewModel"),
) {

    data class State(val isChecking: Boolean)

    /** Null unless the server refuses this version. */
    val state: StateFlow<State?> = combine(
        compatibility.state,
        compatibilityCheck.checking,
    ) { compat, checking ->
        State(isChecking = checking).takeIf { compat == CompatState.UpdateRequired }
    }.stateIn(vmScope, SharingStarted.WhileSubscribed(5000), null)

    fun update() {
        log(tag) { "update()" }
        updateTarget.open()
    }

    fun checkAgain() = launch {
        log(tag) { "checkAgain()" }
        compatibilityCheck.check(force = true)
    }
}
