package eu.darken.apl.main.ui

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.apl.common.SponsorHelper
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.datastore.value
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.github.GithubApi
import eu.darken.apl.common.uix.ViewModel4
import eu.darken.apl.common.units.AircraftUnits
import eu.darken.apl.common.units.DistanceUnitSetting
import eu.darken.apl.feeder.core.config.FeederSettings
import eu.darken.apl.main.core.GeneralSettings
import eu.darken.apl.main.core.ThemeState
import eu.darken.apl.main.core.themeState
import eu.darken.apl.main.core.update.UpdateChecker
import eu.darken.apl.map.core.MapOptions
import eu.darken.apl.map.ui.DestinationMap
import eu.darken.apl.server.compat.AppCompatibility
import eu.darken.apl.server.compat.CompatState
import eu.darken.apl.watch.core.WatchId
import eu.darken.apl.watch.core.WatchRepo
import eu.darken.apl.watch.core.getStatus
import eu.darken.apl.watch.ui.DestinationWatchDetails
import eu.darken.apl.watch.ui.DestinationWatchList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject


@HiltViewModel
class MainActivityVM @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val sponsorHelper: SponsorHelper,
    private val watchRepo: WatchRepo,
    private val updateChecker: UpdateChecker,
    private val generalSettings: GeneralSettings,
    feederSettings: FeederSettings,
    appCompatibility: AppCompatibility,
) : ViewModel4(
    dispatcherProvider = dispatcherProvider,
    tag = logTag("Main", "Activity", "ViewModel")
) {

    private val readyStateInternal = MutableStateFlow(true)
    val readyState = readyStateInternal.asStateFlow()

    val themeState = generalSettings.themeState
        .stateIn(vmScope, SharingStarted.Eagerly, ThemeState())

    val unitSettings = combine(generalSettings.aircraftUnits.flow, generalSettings.distanceUnit.flow) { aircraft, distance ->
        aircraft to distance
    }.stateIn(vmScope, SharingStarted.Eagerly, AircraftUnits.AVIATION to DistanceUnitSetting.AUTOMATIC)

    // null until the setting has been read
    val isFeederTabVisible = feederSettings.isNavTabVisible.flow
        .catch {
            log(TAG, WARN) { "Failed to read feeder tab visibility: $it" }
            emit(true)
        }
        .stateIn<Boolean?>(vmScope, SharingStarted.Eagerly, null)

    val updateRelease = combine(
        flow { emit(updateChecker.checkForUpdate()) },
        generalSettings.dismissedUpdateVersion.flow,
        appCompatibility.state,
    ) { release, dismissed, compat ->
        when {
            release == null -> null
            // While the server asks for an update, the update page asks instead
            compat != CompatState.Ok -> null
            release.tagName == dismissed -> {
                log(TAG) { "Update ${release.tagName} was snoozed, skipping dialog" }
                null
            }

            else -> release
        }
    }.stateIn<GithubApi.ReleaseInfo?>(vmScope, SharingStarted.Lazily, null)

    fun snoozeUpdate(release: GithubApi.ReleaseInfo) = launch {
        log(TAG) { "snoozeUpdate(${release.tagName})" }
        generalSettings.dismissedUpdateVersion.value(release.tagName)
    }

    fun onGo() {
        // Ready
    }

    fun goSponsor() = launch {
        sponsorHelper.openSponsorPage()
    }

    fun showWatchAlert(watchId: WatchId) = launch {
        val status = watchRepo.getStatus(watchId)
        if (status == null) {
            log(TAG, WARN) { "Watch with id $watchId no longer exists" }
            return@launch
        }
        when {
            status.tracked.isNotEmpty() -> {
                val mapOptions = MapOptions.focus(status.tracked.map { it.hex })
                navTo(DestinationMap(mapOptions = mapOptions))
            }

            // The server's alert names aircraft this app may never have loaded, the details show its match
            status.server?.isHeld == true -> navTo(DestinationWatchDetails(watchId))

            else -> log(TAG) { "No aircraft: $status" }
        }
    }

    fun showWatchList() {
        navTo(DestinationWatchList())
    }

    companion object {
        private val TAG = logTag("Main", "Activity", "ViewModel")
    }
}
