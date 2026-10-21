package eu.darken.apl.main.ui.update

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.navigation.NavEvent
import eu.darken.apl.common.uix.ViewModel4
import eu.darken.apl.main.core.update.AppUpdatePrompt
import eu.darken.apl.main.core.update.AppUpdateTarget
import eu.darken.apl.server.compat.AppCompatibilityCheck
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val updatePrompt: AppUpdatePrompt,
    private val compatibilityCheck: AppCompatibilityCheck,
    private val updateTarget: AppUpdateTarget,
) : ViewModel4(
    dispatcherProvider = dispatcherProvider,
    tag = logTag("Update", "ViewModel"),
) {

    val prompt: Flow<UpdatePromptState> = updatePrompt.promptState

    val isChecking: StateFlow<Boolean> = compatibilityCheck.checking

    fun update() {
        log(tag) { "update()" }
        updateTarget.open()
    }

    fun notNow(version: Int) = launch {
        updatePrompt.notNow(version)
    }

    fun checkAgain() = launch {
        log(tag) { "checkAgain()" }
        compatibilityCheck.check(force = true)
    }

    fun continueLimited() {
        updatePrompt.continueLimited()
    }

    fun finish() {
        log(tag) { "finish()" }
        navEvents.tryEmit(NavEvent.Finish)
    }
}
