package eu.darken.apl.watch.ui.settings

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.datastore.value
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.uix.ViewModel4
import eu.darken.apl.upgrade.UpgradeRepo
import eu.darken.apl.upgrade.ui.DestinationUpgrade
import eu.darken.apl.watch.core.WatchSettings
import eu.darken.apl.watch.core.alerts.WatchWorkerHelper
import kotlinx.coroutines.flow.combine
import java.time.Duration
import javax.inject.Inject

@HiltViewModel
class WatchSettingsViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val settings: WatchSettings,
    private val watchWorkerHelper: WatchWorkerHelper,
    upgradeRepo: UpgradeRepo,
) : ViewModel4(
    dispatcherProvider = dispatcherProvider,
    tag = logTag("Settings", "Watch", "VM"),
) {

    val state = combine(
        settings.watchMonitorInterval.flow,
        upgradeRepo.upgradeInfo,
    ) { interval, upgrade ->
        val isPro = upgrade.isSettled && upgrade.isPro
        State(
            // What the checks actually run at, the stored preference may sit below the floor
            currentIntervalMinutes = WatchWorkerHelper.effectiveInterval(interval, isPro).toMinutes().toFloat(),
            floorMinutes = WatchWorkerHelper.minInterval(isPro).toMinutes().toFloat(),
            requiresUpgrade = !isPro,
        )
    }.asStateFlow()

    fun goUpgrade() = navTo(DestinationUpgrade)

    fun updateWatchInterval(interval: Duration) = launch {
        log(tag) { "updateWatchInterval($interval)" }
        settings.watchMonitorInterval.value(interval)
        watchWorkerHelper.updateWorker()
    }

    fun resetWatchInterval() = launch {
        log(tag) { "resetWatchInterval()" }
        settings.watchMonitorInterval.value(WatchSettings.DEFAULT_CHECK_INTERVAL)
        watchWorkerHelper.updateWorker()
    }

    data class State(
        val currentIntervalMinutes: Float,
        val floorMinutes: Float,
        val requiresUpgrade: Boolean,
    )
}
