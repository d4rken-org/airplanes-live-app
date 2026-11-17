package eu.darken.apl.main.ui.onboarding.team

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.apl.common.BuildConfigWrap
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.uix.ViewModel4
import eu.darken.apl.main.ui.DestinationPrivacy
import javax.inject.Inject

@HiltViewModel
class TeamViewModel @Inject constructor(
    @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
) : ViewModel4(
    dispatcherProvider = dispatcherProvider,
    tag = logTag("Team", "ViewModel"),
) {

    val isGplay: Boolean = BuildConfigWrap.FLAVOR == BuildConfigWrap.Flavor.GPLAY

    fun finishTeam() {
        navTo(DestinationPrivacy)
    }
}
