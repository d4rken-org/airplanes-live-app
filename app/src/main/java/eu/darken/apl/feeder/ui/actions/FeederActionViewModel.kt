package eu.darken.apl.feeder.ui.actions

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.apl.common.WebpageTool
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.uix.ViewModel4
import eu.darken.apl.feeder.core.Feeder
import eu.darken.apl.feeder.core.FeederRepo
import eu.darken.apl.feeder.core.ReceiverId
import eu.darken.apl.feeder.core.monitor.FeederMuteController
import eu.darken.apl.feeder.ui.DestinationFeederDetail
import eu.darken.apl.map.core.MapOptions
import eu.darken.apl.map.core.toMapFeedId
import eu.darken.apl.map.ui.DestinationMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class FeederActionViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val feederRepo: FeederRepo,
    private val webpageTool: WebpageTool,
    private val muteController: FeederMuteController,
) : ViewModel4(
    dispatcherProvider = dispatcherProvider,
    tag = logTag("Feeder", "Action", "ViewModel"),
) {

    private var feederId: ReceiverId = ""
    private val trigger = MutableStateFlow(UUID.randomUUID())

    fun init(receiverId: ReceiverId) {
        if (feederId == receiverId) return
        feederId = receiverId
        feederRepo.feeders
            .map { feeders -> feeders.singleOrNull { it.id == feederId } }
            .filter { it == null }
            .take(1)
            .onEach {
                log(tag) { "Feeder $feederId is no longer available" }
                navUp()
            }
            .launchInViewModel()
    }

    val state = combine(
        trigger,
        feederRepo.feeders,
        muteController.mutedFeeders,
    ) { _, feeders, muted ->
        val feeder = feeders.singleOrNull { it.id == feederId } ?: return@combine null
        State(feeder = feeder, isMuted = feeder.id in muted)
    }.asStateFlow()

    fun viewDetails() = launch {
        navTo(DestinationFeederDetail(receiverId = feederId))
    }

    fun toggleMute() = launch {
        val muted = state.first()?.isMuted ?: return@launch
        muteController.setMuted(feederId, !muted)
    }

    fun showFeedOnMap() = launch {
        val feeder = state.first()?.feeder ?: return@launch
        navTo(DestinationMap(mapOptions = MapOptions(feeds = setOf(feeder.id.toMapFeedId()))))
    }

    fun openOnWebsite() = launch {
        val feeder = state.first()?.feeder ?: return@launch
        webpageTool.open("https://globe.airplanes.live/?feed=${feeder.id.toMapFeedId()}")
    }

    data class State(
        val feeder: Feeder,
        val isMuted: Boolean = false,
    )
}
