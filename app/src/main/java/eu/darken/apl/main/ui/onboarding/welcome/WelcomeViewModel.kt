package eu.darken.apl.main.ui.onboarding.welcome

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.uix.ViewModel4
import eu.darken.apl.main.ui.DestinationPrivacy
import javax.inject.Inject

@HiltViewModel
class WelcomeViewModel @Inject constructor(
    @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
) : ViewModel4(
    dispatcherProvider = dispatcherProvider,
    tag = logTag("Welcome", "ViewModel"),
) {

    fun finishWelcome() {
        navTo(DestinationPrivacy)
    }
}
