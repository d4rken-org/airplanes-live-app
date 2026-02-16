package eu.darken.apl.main.ui.settings.support

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.apl.common.PrivacyPolicy
import eu.darken.apl.common.WebpageTool
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.debug.recorder.core.RecorderModule
import eu.darken.apl.common.uix.ViewModel4
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class SupportViewModel @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val recorderModule: RecorderModule,
    private val webpageTool: WebpageTool,
) : ViewModel4(
    dispatcherProvider = dispatcherProvider,
    tag = logTag("Settings", "Support", "VM"),
) {

    val isRecording = recorderModule.state.map { it.isRecording }.asStateFlow()

    fun openDocumentation() {
        webpageTool.open("https://github.com/d4rken-org/airplanes-live-app/wiki")
    }

    fun openIssueTracker() {
        webpageTool.open("https://github.com/d4rken-org/airplanes-live-app/issues")
    }

    fun openAirplanesLiveDiscord() {
        webpageTool.open("https://discord.gg/adsb")
    }

    fun openDarkensDiscord() {
        webpageTool.open("https://discord.gg/ENtVkMHqZg")
    }

    fun openPrivacyPolicy() {
        webpageTool.open(PrivacyPolicy.URL)
    }

    fun startDebugLog() = launch {
        log(tag) { "startDebugLog()" }
        recorderModule.startRecorder()
    }

    fun stopDebugLog() = launch {
        log(tag) { "stopDebugLog()" }
        recorderModule.stopRecorder()
    }
}
