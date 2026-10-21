package eu.darken.apl.main.ui.main

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.uix.ViewModel4
import eu.darken.apl.main.core.GeneralSettings
import eu.darken.apl.main.core.update.AppUpdatePrompt
import eu.darken.apl.main.ui.update.promptState
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    generalSettings: GeneralSettings,
    appUpdatePrompt: AppUpdatePrompt,
) : ViewModel4(
    dispatcherProvider = dispatcherProvider,
    tag = logTag("Main", "ViewModel")
) {

    val isOnboardingFinished = generalSettings.isOnboardingFinished.flow
        .asStateFlow()

    val updatePrompt = appUpdatePrompt.promptState
        .asStateFlow()
}
