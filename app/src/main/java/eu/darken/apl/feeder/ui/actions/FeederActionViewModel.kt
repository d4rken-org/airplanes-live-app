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
import eu.darken.apl.common.chart.ChartState
import eu.darken.apl.feeder.core.stats.MlatChartData
import eu.darken.apl.feeder.ui.add.NewFeederQR
import eu.darken.apl.map.core.MapOptions
import eu.darken.apl.map.core.toMapFeedId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import java.net.Inet4Address
import java.net.InetAddress
import java.net.UnknownHostException
import java.time.Duration
import java.time.Instant
import java.util.UUID
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

    fun setNotifyWhenOffline(enabled: Boolean) = launch {
        log(tag) { "setNotifyWhenOffline($enabled)" }
        feederRepo.setOfflineCheckTimeout(
            feederId,
            if (enabled) FeederConfig.DEFAULT_OFFLINE_LIMIT else null,
        )
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

        feederRepo.setAddress(feederId, validateFeederAddress(address))
    }

    fun showFeedOnMap() = launch {
        log(tag) { "showFeedOnMap()" }
        val feeder = state.first()?.feeder ?: return@launch
        // Only the website knows which aircraft a feeder received
        webpageTool.open(MapOptions(feeds = setOf(feeder.id.toMapFeedId())).createUrl())
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

/**
 * Null for a blank [address], which clears it. A name without a dot, like "feeder" on a home network, has to
 * resolve to an IPv4 address.
 */
internal fun validateFeederAddress(
    address: String,
    resolve: (String) -> InetAddress? = ::resolveOrNull,
): String? {
    if (address.isBlank()) return null
    if (address.isIpv4Literal() || address.isHostName()) return address
    if (resolve(address) is Inet4Address) return address
    throw InvalidFeederAddressException(address)
}

private fun resolveOrNull(host: String): InetAddress? = try {
    InetAddress.getByName(host)
} catch (e: UnknownHostException) {
    null
}

// No leading zeros, browsers read "010" as octal
private fun String.isIpv4Literal(): Boolean {
    val parts = split('.')
    return parts.size == 4 && parts.all { part ->
        part.length in 1..3 && part.all { it in '0'..'9' } && (part.length == 1 || part[0] != '0') && part.toInt() <= 255
    }
}

private val HOST_NAME = Regex("^([a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,63}$")

private fun String.isHostName(): Boolean = length <= 253 && HOST_NAME.matches(this)
