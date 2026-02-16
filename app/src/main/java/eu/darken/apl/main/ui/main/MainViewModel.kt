package eu.darken.apl.main.ui.main

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.apl.common.BuildConfigWrap
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.Logging.Priority.ERROR
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.github.GithubApi
import eu.darken.apl.common.github.GithubReleaseCheck
import eu.darken.apl.common.uix.ViewModel4
import eu.darken.apl.main.core.GeneralSettings
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import net.swiftzer.semver.SemVer
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    githubReleaseCheck: GithubReleaseCheck,
    private val generalSettings: GeneralSettings,
) : ViewModel4(
    dispatcherProvider = dispatcherProvider,
    tag = logTag("Main", "ViewModel")
) {

    val isOnboardingFinished = generalSettings.isOnboardingFinished.flow
        .asStateFlow()

    val newRelease = flow {
        val latestRelease = try {
            githubReleaseCheck.latestRelease("d4rken", "airplanes-live-app")
        } catch (e: Exception) {
            log(tag, ERROR) { "Release check failed: ${e.asLog()}" }
            null
        }
        emit(latestRelease)
    }
        .filterNotNull()
        .filter {
            val current = try {
                SemVer.parse(BuildConfigWrap.VERSION_NAME.removePrefix("v"))
            } catch (e: IllegalArgumentException) {
                log(tag, ERROR) { "Failed to parse current version: ${e.asLog()}" }
                return@filter false
            }
            log(tag) { "Current version is $current" }

            val latest = try {
                SemVer.parse(it.tagName.removePrefix("v"))
            } catch (e: IllegalArgumentException) {
                log(tag, ERROR) { "Failed to parse current version: ${e.asLog()}" }
                return@filter false
            }
            log(tag) { "Latest version is $latest" }
            current < latest
        }
        .asStateFlow()

    data class State(
        val isOnboardingFinished: Boolean = true,
        val newRelease: GithubApi.ReleaseInfo? = null,
    )
}
