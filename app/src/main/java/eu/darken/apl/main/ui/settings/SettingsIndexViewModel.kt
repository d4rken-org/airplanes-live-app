package eu.darken.apl.main.ui.settings

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.apl.common.PrivacyPolicy
import eu.darken.apl.common.SponsorHelper
import eu.darken.apl.common.WebpageTool
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.github.GithubApi
import eu.darken.apl.common.uix.ViewModel4
import eu.darken.apl.main.core.update.UpdateChecker
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

@HiltViewModel
class SettingsIndexViewModel @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val webpageTool: WebpageTool,
    private val sponsorHelper: SponsorHelper,
    updateChecker: UpdateChecker,
) : ViewModel4(
    dispatcherProvider = dispatcherProvider,
    tag = logTag("Settings", "Index", "VM"),
) {

    val newRelease = flow {
        emit(updateChecker.checkForUpdate())
    }.asStateFlow()

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

    fun openUpdate(release: GithubApi.ReleaseInfo) {
        log(tag) { "openUpdate(${release.tagName})" }
        val apkAsset = release.assets.find { it.name.endsWith(".apk", ignoreCase = true) }
        val url = apkAsset?.downloadUrl ?: release.htmlUrl
        webpageTool.open(url)
    }

    fun goSupport() = navTo(DestinationSupport)

    fun goAcknowledgements() = navTo(DestinationAcknowledgements)

    fun goPrivacyPolicy() {
        log(tag) { "goPrivacyPolicy()" }
        webpageTool.open(PrivacyPolicy.URL)
    }
}
