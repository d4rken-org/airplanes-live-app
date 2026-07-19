package eu.darken.apl.feeder.ui.settings

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.datastore.value
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.uix.ViewModel4
import eu.darken.apl.feeder.core.config.FeederSettings
import eu.darken.apl.feeder.core.monitor.FeederWorkerHelper
import kotlinx.coroutines.flow.combine
import java.time.Duration
import javax.inject.Inject

@HiltViewModel
class FeederSettingsViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val settings: FeederSettings,
    private val feederWorkerHelper: FeederWorkerHelper,
) : ViewModel4(
    dispatcherProvider = dispatcherProvider,
    tag = logTag("Settings", "Feeder", "VM"),
) {

    val state = combine(
        settings.feederMonitorInterval.flow,
        settings.deviceHealthWarningsEnabled.flow,
    ) { interval, healthWarnings ->
        State(
            currentIntervalMinutes = interval.toMinutes().toFloat(),
            healthWarningsEnabled = healthWarnings,
        )
    }.asStateFlow()

    fun updateFeederInterval(interval: Duration) = launch {
        log(tag) { "updateFeederInterval($interval)" }
        settings.feederMonitorInterval.value(interval)
        feederWorkerHelper.updateWorker()
    }

    fun setHealthWarningsEnabled(enabled: Boolean) = launch {
        log(tag) { "setHealthWarningsEnabled($enabled)" }
        settings.deviceHealthWarningsEnabled.value(enabled)
        // Immediate effect either way: enabling runs a first check, disabling lets the cycle
        // retire any shown health notifications right away.
        feederWorkerHelper.triggerNow()
    }

    fun resetFeederInterval() = launch {
        log(tag) { "resetFeederInterval()" }
        settings.feederMonitorInterval.value(FeederSettings.DEFAULT_CHECK_INTERVAL)
        feederWorkerHelper.updateWorker()
    }

    data class State(
        val currentIntervalMinutes: Float,
        val healthWarningsEnabled: Boolean = false,
    )
}
