package eu.darken.apl.feeder.ui.actions

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.apl.common.WebpageTool
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.flow.SingleEventFlow
import eu.darken.apl.common.uix.ViewModel4
import eu.darken.apl.feeder.core.Feeder
import eu.darken.apl.feeder.core.FeederRepo
import eu.darken.apl.feeder.core.ReceiverId
import eu.darken.apl.feeder.core.config.FeederConfig
import eu.darken.apl.feeder.core.stats.BeastChartData
import eu.darken.apl.feeder.core.stats.ChartState
import eu.darken.apl.feeder.core.stats.MlatChartData
import eu.darken.apl.feeder.ui.add.NewFeederQR
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
import java.net.Inet4Address
import java.net.InetAddress
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.regex.Pattern
import javax.inject.Inject

@HiltViewModel
class FeederActionViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val feederRepo: FeederRepo,
    private val webpageTool: WebpageTool,
) : ViewModel4(
    dispatcherProvider = dispatcherProvider,
    tag = logTag("Feeder", "Action", "Dialog", "ViewModel"),
) {

    private var feederId: ReceiverId = ""
    private val trigger = MutableStateFlow(UUID.randomUUID())
    val events = SingleEventFlow<FeederActionEvents>()

    private val chartData = MutableStateFlow<Pair<BeastChartData, MlatChartData>?>(null)

    fun init(receiverId: ReceiverId) {
        if (feederId == receiverId) return
        feederId = receiverId
        feederRepo.feeders
            .map { feeders -> feeders.singleOrNull { it.id == feederId } }
            .filter { it == null }
            .take(1)
            .onEach {
                log(tag) { "App data for $feederId is no longer available" }
                navUp()
            }
            .launchInViewModel()

        launch {
            val since30d = Instant.now().minus(Duration.ofDays(30))
            val beast = feederRepo.getBeastChartData(feederId, since30d)
            val mlat = feederRepo.getMlatChartData(feederId, since30d)
            chartData.value = beast to mlat
        }
    }

    val state = combine(
        trigger,
        feederRepo.feeders,
        chartData,
    ) { _, feeders, charts ->
        val feeder = feeders.singleOrNull { it.id == feederId } ?: return@combine null
        State(
            feeder = feeder,
            beastChartState = charts?.first?.let {
                if (it.messageRate.size >= 2) ChartState.Ready(it) else ChartState.NoData
            } ?: ChartState.Loading,
            mlatChartState = charts?.second?.let {
                if (it.messageRate.size >= 2 && it.outlierPercent.size >= 2) ChartState.Ready(it) else ChartState.NoData
            } ?: ChartState.Loading,
        )
    }.asStateFlow()

    fun removeFeeder(confirmed: Boolean = false) = launch {
        log(tag) { "removeFeeder()" }
        if (!confirmed) {
            events.emit(FeederActionEvents.RemovalConfirmation(feederId))
            return@launch
        }
        feederRepo.removeFeeder(feederId)
    }

    fun toggleNotifyWhenOffline() = launch {
        log(tag) { "toggleNotifyWhenOffline()" }
        val newTimeout = if (state.first()?.feeder?.config?.offlineCheckTimeout != null) {
            null
        } else {
            FeederConfig.DEFAULT_OFFLINE_LIMIT
        }
        feederRepo.setOfflineCheckTimeout(feederId, newTimeout)
    }

    fun renameFeeder(newName: String? = null) = launch {
        log(tag) { "renameFeeder($newName)" }
        if (newName == null) {
            state.first()?.feeder?.let { events.emit(FeederActionEvents.Rename(it)) }
            return@launch
        }
        feederRepo.setLabel(feederId, newName.takeIf { it.isNotBlank() })
    }

    fun changeAddress(address: String? = null) = launch {
        log(tag) { "changeAddress($address)" }
        if (address == null) {
            state.first()?.feeder?.let { events.emit(FeederActionEvents.ChangeIpAddress(it)) }
            return@launch
        }

        feederRepo.setAddress(
            feederId,
            address.takeIf { it.isNotBlank() }?.let {
                val validIp = InetAddress.getByName(it) is Inet4Address
                val validTld = Pattern.compile("^[a-zA-Z0-9-]{2,256}\\.[a-zA-Z]{2,6}$").matcher(it).matches()
                if (!validIp && !validTld) throw IllegalArgumentException("Invalid address: $address")
                it
            }
        )
    }

    fun showFeedOnMap() = launch {
        log(tag) { "showFeedOnMap()" }
        val feeder = state.first()?.feeder ?: return@launch
        navTo(DestinationMap(mapOptions = MapOptions(feeds = setOf(feeder.id.toMapFeedId()))))
    }

    fun openTar1090() = launch {
        log(tag) { "openTar1090()" }
        val feeder = state.first()?.feeder ?: return@launch
        webpageTool.open("http://${feeder.config.address}/tar1090")
    }

    fun openGraphs1090() = launch {
        log(tag) { "openGraphs1090()" }
        val feeder = state.first()?.feeder ?: return@launch
        webpageTool.open("http://${feeder.config.address}/graphs1090")
    }

    fun generateQrCode() = launch {
        log(tag) { "generateQrCode()" }
        val feeder = state.first()?.feeder ?: return@launch
        val qr = NewFeederQR(
            receiverId = feederId,
            receiverLabel = feeder.label,
            receiverIpv4Address = feeder.config.address,
            position = feeder.config.position
        )
        log(tag) { "generateQrCode(): $feeder -> $qr" }
        events.emit(FeederActionEvents.ShowQrCode(qr))
    }

    data class State(
        val feeder: Feeder,
        val beastChartState: ChartState<BeastChartData> = ChartState.Loading,
        val mlatChartState: ChartState<MlatChartData> = ChartState.Loading,
    )
}
