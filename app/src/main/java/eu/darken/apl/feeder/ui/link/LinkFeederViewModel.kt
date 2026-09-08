package eu.darken.apl.feeder.ui.link

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.uix.ViewModel4
import eu.darken.apl.feeder.core.FeederDiscovery
import eu.darken.apl.feeder.core.FeederRepo
import eu.darken.apl.feeder.core.link.FeederLinkRepo
import eu.darken.apl.server.api.ServerApiException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class LinkFeederViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val linkRepo: FeederLinkRepo,
    private val feederDiscovery: FeederDiscovery,
    private val feederRepo: FeederRepo,
) : ViewModel4(
    dispatcherProvider = dispatcherProvider,
    tag = logTag("Feeder", "Link", "VM"),
) {

    data class State(
        val input: String = "",
        val detected: List<String> = emptyList(),
        val monitored: List<String> = emptyList(),
        val isBusy: Boolean = false,
        /** Server error code, the dialog turns it into a message. */
        val errorCode: String? = null,
        val failed: Boolean = false,
    )

    val state = MutableStateFlow(State())

    init {
        launch {
            val monitored = feederRepo.feeders.first().map { it.id }
            state.value = state.value.copy(monitored = monitored)
        }
    }

    fun updateInput(input: String) {
        state.value = state.value.copy(input = input, errorCode = null, failed = false)
    }

    fun detect() = launch {
        log(tag) { "detect()" }
        state.value = state.value.copy(isBusy = true, errorCode = null, failed = false)
        try {
            state.value = state.value.copy(detected = feederDiscovery.detect().map { it.uuid.toString() })
        } catch (e: Exception) {
            log(tag, WARN) { "Detection failed: ${e.asLog()}" }
            state.value = state.value.copy(failed = true)
        } finally {
            state.value = state.value.copy(isBusy = false)
        }
    }

    fun link(feederId: String) = launch {
        log(tag) { "link(...)" }
        state.value = state.value.copy(isBusy = true, errorCode = null, failed = false)
        try {
            linkRepo.register(feederId.trim())
            navUp()
        } catch (e: ServerApiException) {
            log(tag, WARN) { "Linking rejected: ${e.code}" }
            state.value = state.value.copy(errorCode = e.code)
        } catch (e: IOException) {
            log(tag, WARN) { "Linking failed: ${e.asLog()}" }
            state.value = state.value.copy(failed = true)
        } finally {
            state.value = state.value.copy(isBusy = false)
        }
    }
}
