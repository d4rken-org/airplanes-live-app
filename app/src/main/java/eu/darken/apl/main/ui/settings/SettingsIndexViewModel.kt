package eu.darken.apl.main.ui.settings

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.apl.common.PrivacyPolicy
import eu.darken.apl.common.SponsorHelper
import eu.darken.apl.common.WebpageTool
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.uix.ViewModel4
import javax.inject.Inject

@HiltViewModel
class SettingsIndexViewModel @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val webpageTool: WebpageTool,
    private val sponsorHelper: SponsorHelper,
) : ViewModel4(
    dispatcherProvider = dispatcherProvider,
    tag = logTag("Settings", "Index", "VM"),
) {

    fun goGeneralSettings() = navTo(DestinationGeneralSettings)

    fun goMapSettings() = navTo(DestinationMapSettings)

    fun goWatchSettings() = navTo(DestinationWatchSettings)

    fun goFeederSettings() = navTo(DestinationFeederSettings)

    fun goSponsor() = launch {
        log(tag) { "goSponsor()" }
        sponsorHelper.openSponsorPage()
    }

    fun goChangelog() {
        log(tag) { "goChangelog()" }
        webpageTool.open("https://github.com/d4rken-org/airplanes-live-app/releases/latest")
    }

    fun goSupport() = navTo(DestinationSupport)

    fun goAcknowledgements() = navTo(DestinationAcknowledgements)

    fun goPrivacyPolicy() {
        log(tag) { "goPrivacyPolicy()" }
        webpageTool.open(PrivacyPolicy.URL)
    }
}
