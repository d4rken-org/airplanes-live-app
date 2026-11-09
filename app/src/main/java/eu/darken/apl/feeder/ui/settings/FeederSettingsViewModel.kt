package eu.darken.apl.feeder.ui.settings

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.datastore.value
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.uix.ViewModel4
import eu.darken.apl.feeder.core.config.FeederSettings
import eu.darken.apl.feeder.core.monitor.FeederWorkerHelper
import eu.darken.apl.feeder.core.server.FeederServerMode
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Duration
import javax.inject.Inject

@HiltViewModel
class FeederSettingsViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val settings: FeederSettings,
    private val feederWorkerHelper: FeederWorkerHelper,
    feederServerMode: FeederServerMode,
) : ViewModel4(
    dispatcherProvider = dispatcherProvider,
    tag = logTag("Settings", "Feeder", "VM"),
) {

    val state = combine(
        settings.feederMonitorInterval.flow,
        settings.isNavTabVisible.flow,
        settings.notifyProblems.flow,
        feederServerMode.state.map { it.isActive },
    ) { interval, isNavTabVisible, notifyProblems, isServerMode ->
        State(
            currentIntervalMinutes = interval.toMinutes().toFloat(),
            isNavTabVisible = isNavTabVisible,
            isServerMode = isServerMode,
            notifyProblems = notifyProblems,
        )
    }.asStateFlow()

    fun setNotifyProblems(enabled: Boolean) = launch {
        log(tag) { "setNotifyProblems($enabled)" }
        settings.notifyProblems.value(enabled)
    }

    fun setNavTabVisible(visible: Boolean) = launch {
        log(tag) { "setNavTabVisible($visible)" }
        settings.isNavTabVisible.value(visible)
    }

    fun updateFeederInterval(interval: Duration) = launch {
        log(tag) { "updateFeederInterval($interval)" }
        settings.feederMonitorInterval.value(interval)
        feederWorkerHelper.updateWorker()
    }

    fun resetFeederInterval() = launch {
        log(tag) { "resetFeederInterval()" }
        settings.feederMonitorInterval.value(FeederSettings.DEFAULT_CHECK_INTERVAL)
        feederWorkerHelper.updateWorker()
    }

    data class State(
        val currentIntervalMinutes: Float,
        val isNavTabVisible: Boolean,
        val isServerMode: Boolean = false,
        val notifyProblems: Boolean = true,
    )
}
