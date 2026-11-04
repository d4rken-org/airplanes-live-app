package eu.darken.apl.screenshots

import android.location.Location
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import eu.darken.apl.common.chart.ChartPoint
import eu.darken.apl.common.compose.preview.FakeAircraft
import eu.darken.apl.common.navigation.LocalNavigationController
import eu.darken.apl.common.navigation.NavigationController
import eu.darken.apl.common.theming.AplTheme
import eu.darken.apl.common.theming.ThemeMode
import eu.darken.apl.feeder.core.link.FeederLinkRepo
import eu.darken.apl.feeder.core.stats.BeastStatsEntity
import eu.darken.apl.feeder.core.stats.MlatStatsEntity
import eu.darken.apl.feeder.ui.FeederListScreen
import eu.darken.apl.feeder.ui.FeederListViewModel
import eu.darken.apl.feeder.ui.preview.mockFeeder
import eu.darken.apl.main.core.ThemeState
import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.main.core.aircraft.isLiveAt
import eu.darken.apl.main.ui.navigation.BottomNavBar
import eu.darken.apl.main.ui.navigation.MainTab
import eu.darken.apl.search.core.SearchInput
import eu.darken.apl.search.ui.SearchScreen
import eu.darken.apl.search.ui.SearchViewModel
import eu.darken.apl.server.api.LinkedFeeder
import eu.darken.apl.watch.core.WatchId
import eu.darken.apl.watch.core.history.WatchCheck
import eu.darken.apl.watch.core.types.AircraftWatch
import eu.darken.apl.watch.core.types.SquawkWatch
import eu.darken.apl.watch.ui.WatchListScreen
import eu.darken.apl.watch.ui.WatchListViewModel
import eu.darken.apl.watch.ui.preview.mockAircraftWatch
import eu.darken.apl.watch.ui.preview.mockFlightWatchStatus
import eu.darken.apl.watch.ui.preview.mockSquawkWatch
import java.time.Duration
import java.time.Instant
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos

internal const val DS = "spec:width=1080px,height=2400px,dpi=428"

@Composable
internal fun ScreenshotWrapper(content: @Composable () -> Unit) {
    AplTheme(state = ThemeState(mode = ThemeMode.LIGHT)) {
        CompositionLocalProvider(LocalNavigationController provides NavigationController()) {
            Surface { content() }
        }
    }
}

private val fixedTime = Instant.parse("2026-01-15T12:00:00Z")

private fun location(latitude: Double, longitude: Double) = Location("screenshot").apply {
    this.latitude = latitude
    this.longitude = longitude
}

// Frankfurt
private val ourLocation = location(50.1109, 8.6821)

private val mockAircraft1 = FakeAircraft(
    hex = "3C6752",
    registration = "D-AIMA",
    callsign = "DLH456",
    operator = "Lufthansa",
    airframe = "A380",
    description = "Airbus A380-841",
    squawk = "1000",
    altitudeFt = 38000,
    groundSpeed = 480f,
    location = location(50.5800, 8.7300),
    messageSeenAt = fixedTime.minusSeconds(2),
    positionSeenAt = fixedTime.minusSeconds(2),
    fetchedAt = fixedTime,
)

private val mockAircraft2 = FakeAircraft(
    hex = "A12345",
    registration = "N12345",
    callsign = "UAL789",
    operator = "United Airlines",
    airframe = "B789",
    description = "Boeing 787-9",
    squawk = "2456",
    altitudeFt = 35000,
    groundSpeed = 510f,
    location = location(49.0200, 8.9500),
    messageSeenAt = fixedTime.minusSeconds(4),
    positionSeenAt = fixedTime.minusSeconds(4),
    fetchedAt = fixedTime,
)

private val mockAircraft3 = FakeAircraft(
    hex = "4CA87D",
    registration = "EI-DPS",
    callsign = "RYR1234",
    operator = "Ryanair",
    airframe = "B738",
    description = "Boeing 737-8AS",
    squawk = "3421",
    altitudeFt = 28000,
    groundSpeed = 420f,
    location = location(50.0500, 8.5000),
    messageSeenAt = fixedTime.minusSeconds(1),
    positionSeenAt = fixedTime.minusSeconds(1),
    fetchedAt = fixedTime,
)

private fun Aircraft.distanceFromUs(): Float? = location?.let { ourLocation.distanceTo(it) }

@Composable
internal fun SearchContent() {
    val results = listOf(mockAircraft1, mockAircraft2, mockAircraft3)
    ScreenshotWrapper {
        TabShell(tab = MainTab.SEARCH) {
            SearchScreen(
                state = SearchViewModel.State(
                    input = SearchInput(text = results.joinToString(", ") { it.callsign!! }),
                    items = listOf(SearchViewModel.SearchItem.Summary(aircraftCount = results.size)) +
                        results.map {
                            SearchViewModel.SearchItem.AircraftResult(
                                aircraft = it,
                                watch = null,
                                distanceInMeter = it.distanceFromUs(),
                            )
                        },
                    nowMillis = fixedTime.toEpochMilli(),
                ),
                snackbarHostState = remember { SnackbarHostState() },
                onClear = {},
                onSubmit = {},
                onToggleCategoryChip = {},
                onToggleNearby = {},
                onNearbyPlace = {},
                onAircraftClick = {},
                onThumbnailClick = {},
                onWatchClick = {},
                onShowOnMap = {},
                onGrantLocation = {},
                onDismissLocation = {},
                onStartFeeding = {},
                serverNow = { fixedTime.toEpochMilli() },
            )
        }
    }
}

private fun sighting(watchId: WatchId, aircraft: Collection<Aircraft>) = WatchCheck(
    watchId = watchId,
    checkAt = fixedTime.minusSeconds(30),
    aircraftCount = aircraft.size,
    seenHexes = aircraft.map { it.hex }.toSet(),
)

@Composable
internal fun WatchContent() {
    val aircraftWatch = mockAircraftWatch(hex = mockAircraft1.hex)
    val aircraftWatchTracked = setOf(mockAircraft1)
    val aircraftWatchStatus = AircraftWatch.Status(
        watch = aircraftWatch,
        lastCheck = sighting(aircraftWatch.id, aircraftWatchTracked),
        lastHit = sighting(aircraftWatch.id, aircraftWatchTracked),
        tracked = aircraftWatchTracked,
    )

    val squawkWatch = mockSquawkWatch(code = "7700")
    val squawkWatchTracked = setOf(
        mockAircraft2.copy(squawk = squawkWatch.code),
        mockAircraft3.copy(squawk = squawkWatch.code),
    )
    val squawkWatchStatus = SquawkWatch.Status(
        watch = squawkWatch,
        lastCheck = sighting(squawkWatch.id, squawkWatchTracked),
        lastHit = sighting(squawkWatch.id, squawkWatchTracked),
        tracked = squawkWatchTracked,
    )

    ScreenshotWrapper {
        TabShell(tab = MainTab.WATCH) {
            WatchListScreen(
                state = WatchListViewModel.State(
                    items = listOf(
                        WatchListViewModel.WatchItem.Single(
                            status = aircraftWatchStatus,
                            aircraft = mockAircraft1,
                            ourLocation = ourLocation,
                            liveCount = aircraftWatchTracked.count { it.isLiveAt(fixedTime) },
                        ),
                        WatchListViewModel.WatchItem.Single(
                            status = mockFlightWatchStatus(callsign = "BAW123"),
                            aircraft = null,
                            ourLocation = ourLocation,
                        ),
                        WatchListViewModel.WatchItem.Multi(
                            status = squawkWatchStatus,
                            ourLocation = ourLocation,
                            liveCount = squawkWatchTracked.count { it.isLiveAt(fixedTime) },
                        ),
                    ),
                    now = fixedTime,
                ),
                onRefresh = {},
                onAddWatch = {},
                onWatchClick = {},
                onThumbnailClick = {},
                onAircraftTap = {},
                onShowAllTracked = {},
                onDeleteSelected = {},
                onSortModeSelected = {},
            )
        }
    }
}

/**
 * Hourly BEAST message rates for the past week that end at [currentRate], with daytime peaks and
 * quiet nights like real traffic.
 */
private fun weekOfMessageRates(now: Instant, currentRate: Double, seed: Long): List<ChartPoint> {
    val random = Random(seed)
    val hours = 7 * 24
    return (hours downTo 0).map { hoursAgo ->
        val hourOfDay = Math.floorMod(14 - hoursAgo, 24)
        val daytime = 0.5 + 0.5 * cos(2 * PI * (hourOfDay - 14) / 24)
        val value = if (hoursAgo == 0) {
            currentRate
        } else {
            currentRate * (0.2 + 0.8 * daytime) * (0.9 + 0.2 * random.nextDouble())
        }
        ChartPoint(now.minus(Duration.ofHours(hoursAgo.toLong())), value)
    }
}

@Composable
internal fun FeederContent() {
    // The feeder list measures "last seen" against the device clock, not fixedTime
    val now = Instant.now()
    val feeders = listOf(
        FeederListViewModel.FeederItem(
            feeder = mockFeeder(label = "Home Feeder", id = "abc12").copy(
                beastStats = BeastStatsEntity(
                    receiverId = "abc12",
                    receivedAt = now.minusSeconds(60),
                    positionRate = 41.7,
                    positions = 148_210,
                    messageRate = 612.4,
                    bandwidth = 93.8,
                    connectionTime = 1_209_600,
                    latency = 38,
                ),
                mlatStats = MlatStatsEntity(
                    receiverId = "abc12",
                    receivedAt = now.minusSeconds(60),
                    messageRate = 18.6,
                    peerCount = 27,
                    badSyncTimeout = 0,
                    outlierPercent = 1.3f,
                ),
            ),
            isOffline = false,
            beastSparkline = weekOfMessageRates(now, currentRate = 612.4, seed = 1),
        ),
        FeederListViewModel.FeederItem(
            feeder = mockFeeder(label = "Office Feeder", id = "def34").copy(
                beastStats = BeastStatsEntity(
                    receiverId = "def34",
                    receivedAt = now.minusSeconds(75),
                    positionRate = 19.2,
                    positions = 67_455,
                    messageRate = 284.9,
                    bandwidth = 44.1,
                    connectionTime = 432_000,
                    latency = 52,
                ),
                mlatStats = MlatStatsEntity(
                    receiverId = "def34",
                    receivedAt = now.minusSeconds(75),
                    messageRate = 7.2,
                    peerCount = 12,
                    badSyncTimeout = 0,
                    outlierPercent = 2.8f,
                ),
            ),
            isOffline = false,
            beastSparkline = weekOfMessageRates(now, currentRate = 284.9, seed = 2),
        ),
        FeederListViewModel.FeederItem(
            feeder = mockFeeder(label = "Remote Station", id = "ghi56").copy(
                beastStats = BeastStatsEntity(
                    receiverId = "ghi56",
                    receivedAt = now.minus(Duration.ofDays(3)),
                    positionRate = 8.4,
                    positions = 29_310,
                    messageRate = 153.2,
                    bandwidth = 23.6,
                    connectionTime = 259_200,
                    latency = 61,
                ),
                mlatStats = MlatStatsEntity(
                    receiverId = "ghi56",
                    receivedAt = now.minus(Duration.ofDays(3)),
                    messageRate = 3.1,
                    peerCount = 6,
                    badSyncTimeout = 0,
                    outlierPercent = 4.5f,
                ),
            ),
            isOffline = true,
        ),
    )

    val linkedFeeder = LinkedFeeder(
        feederId = feeders.first().feeder.id,
        status = "active",
        networkVerified = true,
        linkedAt = now.minus(Duration.ofDays(30)).toEpochMilli(),
        checkedAt = now.minus(Duration.ofHours(1)).toEpochMilli(),
        lastActiveAt = now.minusSeconds(60).toEpochMilli(),
        validUntil = now.plus(Duration.ofDays(30)).toEpochMilli(),
        nextCheckAt = now.plus(Duration.ofHours(23)).toEpochMilli(),
        eligible = true,
    )

    ScreenshotWrapper {
        TabShell(tab = MainTab.FEEDER) {
            FeederListScreen(
                state = FeederListViewModel.State(
                    linkState = FeederLinkRepo.FeederLinkState.Linked(feeder = linkedFeeder, tier = "feeder"),
                    isPro = true,
                    feeders = feeders,
                    feederCount = feeders.size,
                    header = FeederListViewModel.Header.SOME_OFFLINE,
                ),
                onRefresh = {},
                onAddFeeder = {},
                onFeederClick = {},
                onSortModeSelected = {},
                onShowOnMap = {},
                onStartFeeding = {},
            )
        }
    }
}

@Composable
private fun TabShell(tab: MainTab, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            content()
        }
        BottomNavBar(selectedTab = tab)
    }
}

@Preview(name = "Search", device = DS)
@Composable
private fun SearchPreview() = SearchContent()

@Preview(name = "Watch", device = DS)
@Composable
private fun WatchPreview() = WatchContent()

@Preview(name = "Feeders", device = DS)
@Composable
private fun FeedersPreview() = FeederContent()
