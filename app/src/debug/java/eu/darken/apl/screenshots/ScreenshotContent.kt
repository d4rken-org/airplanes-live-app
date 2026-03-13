package eu.darken.apl.screenshots

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.CameraAlt
import androidx.compose.material.icons.twotone.Fullscreen
import androidx.compose.material.icons.twotone.MyLocation
import androidx.compose.material.icons.twotone.Refresh
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material.icons.twotone.Tune
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eu.darken.apl.R
import eu.darken.apl.common.compose.BottomNavBar
import eu.darken.apl.common.compose.aplContentWindowInsets
import eu.darken.apl.common.compose.preview.FakeAircraft
import eu.darken.apl.common.navigation.LocalNavigationController
import eu.darken.apl.common.navigation.NavigationController
import eu.darken.apl.common.theming.AplTheme
import eu.darken.apl.common.theming.ThemeMode
import eu.darken.apl.feeder.core.config.FeederSortMode
import eu.darken.apl.feeder.ui.FeederListScreen
import eu.darken.apl.feeder.ui.FeederListViewModel
import eu.darken.apl.feeder.ui.preview.mockFeeder
import eu.darken.apl.main.core.ThemeState
import eu.darken.apl.search.ui.SearchScreen
import eu.darken.apl.search.ui.SearchViewModel
import eu.darken.apl.watch.core.WatchSortMode
import eu.darken.apl.watch.ui.WatchListScreen
import eu.darken.apl.watch.ui.WatchListViewModel
import eu.darken.apl.watch.ui.preview.mockAircraftWatchStatus
import eu.darken.apl.watch.ui.preview.mockFlightWatchStatus
import eu.darken.apl.watch.ui.preview.mockSquawkWatchStatus
import java.time.Instant

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

private val mockAircraft1 = FakeAircraft(
    hex = "3C6752",
    registration = "D-AIMA",
    callsign = "DLH456",
    operator = "Lufthansa",
    airframe = "A380",
    description = "Airbus A380-841",
    squawk = "1000",
    altitude = "38000",
    groundSpeed = 480f,
    seenAt = fixedTime,
)

private val mockAircraft2 = FakeAircraft(
    hex = "A12345",
    registration = "N12345",
    callsign = "UAL789",
    operator = "United Airlines",
    airframe = "B789",
    description = "Boeing 787-9",
    squawk = "2456",
    altitude = "35000",
    groundSpeed = 510f,
    seenAt = fixedTime,
)

private val mockAircraft3 = FakeAircraft(
    hex = "4CA87D",
    registration = "EI-DPS",
    callsign = "RYR1234",
    operator = "Ryanair",
    airframe = "B738",
    description = "Boeing 737-8AS",
    squawk = "3421",
    altitude = "28000",
    groundSpeed = 420f,
    seenAt = fixedTime,
)

@Composable
internal fun MapContent() {
    ScreenshotWrapper {
        Scaffold(
            contentWindowInsets = aplContentWindowInsets(hasBottomNav = true),
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(stringResource(R.string.app_name))
                            Text(
                                text = "AirplanesLive 24/7",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {}) {
                            Icon(Icons.TwoTone.MyLocation, contentDescription = null)
                        }
                        IconButton(onClick = {}) {
                            Icon(Icons.TwoTone.Refresh, contentDescription = null)
                        }
                        IconButton(onClick = {}) {
                            Icon(Icons.TwoTone.Settings, contentDescription = null)
                        }
                    },
                )
            },
            bottomBar = { BottomNavBar(selectedTab = 0) },
        ) { contentPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            ) {
                Image(
                    painter = painterResource(R.drawable.screenshot_map_background),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalIconButton(
                        onClick = {},
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(Icons.TwoTone.Fullscreen, contentDescription = null)
                    }
                    FilledTonalIconButton(
                        onClick = {},
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(Icons.TwoTone.CameraAlt, contentDescription = null)
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                ) {
                    FilledTonalIconButton(
                        onClick = {},
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(Icons.TwoTone.Tune, contentDescription = null)
                    }
                }
            }
        }
    }
}

@Composable
internal fun SearchContent() {
    ScreenshotWrapper {
        SearchScreen(
            state = SearchViewModel.State(
                input = SearchViewModel.Input(
                    raw = "",
                    mode = SearchViewModel.State.Mode.ALL,
                ),
                items = listOf(
                    SearchViewModel.SearchItem.Summary(aircraftCount = 3),
                    SearchViewModel.SearchItem.AircraftResult(
                        aircraft = mockAircraft1,
                        watch = null,
                        distanceInMeter = 52_000f,
                    ),
                    SearchViewModel.SearchItem.AircraftResult(
                        aircraft = mockAircraft2,
                        watch = null,
                        distanceInMeter = 128_000f,
                    ),
                    SearchViewModel.SearchItem.AircraftResult(
                        aircraft = mockAircraft3,
                        watch = null,
                        distanceInMeter = 15_000f,
                    ),
                ),
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onSearchText = {},
            onModeSelected = {},
            onPositionHome = {},
            onSettings = {},
            onAircraftClick = {},
            onThumbnailClick = {},
            onWatchClick = {},
            onShowOnMap = {},
            onGrantLocation = {},
            onDismissLocation = {},
            onStartFeeding = {},
        )
    }
}

@Composable
internal fun WatchContent() {
    ScreenshotWrapper {
        WatchListScreen(
            state = WatchListViewModel.State(
                items = listOf(
                    WatchListViewModel.WatchItem.Single(
                        status = mockAircraftWatchStatus(aircraft = mockAircraft1),
                        aircraft = mockAircraft1,
                        ourLocation = null,
                    ),
                    WatchListViewModel.WatchItem.Single(
                        status = mockFlightWatchStatus(callsign = "BAW123"),
                        aircraft = null,
                        ourLocation = null,
                    ),
                    WatchListViewModel.WatchItem.Multi(
                        status = mockSquawkWatchStatus(aircraft = setOf(mockAircraft2, mockAircraft3)),
                        ourLocation = null,
                    ),
                ),
            ),
            onRefresh = {},
            onAddWatch = {},
            onSettings = {},
            onWatchClick = {},
            onThumbnailClick = {},
            onAircraftTap = {},
            onShowSquawkInSearch = {},
            onDeleteSelected = {},
            onSortModeSelected = {},
        )
    }
}

@Composable
internal fun FeederContent() {
    ScreenshotWrapper {
        FeederListScreen(
            state = FeederListViewModel.State(
                feeders = listOf(
                    FeederListViewModel.FeederItem(
                        feeder = mockFeeder(label = "Home Feeder", id = "abc12"),
                        isOffline = false,
                    ),
                    FeederListViewModel.FeederItem(
                        feeder = mockFeeder(label = "Office Feeder", id = "def34"),
                        isOffline = false,
                    ),
                    FeederListViewModel.FeederItem(
                        feeder = mockFeeder(label = "Remote Station", id = "ghi56"),
                        isOffline = true,
                    ),
                ),
                feederCount = 3,
            ),
            onRefresh = {},
            onAddFeeder = {},
            onSettings = {},
            onFeederClick = {},
            onSortModeSelected = {},
            onShowOnMap = {},
            onStartFeeding = {},
        )
    }
}

@Preview(name = "Map", device = DS)
@Composable
private fun MapPreview() = MapContent()

@Preview(name = "Search", device = DS)
@Composable
private fun SearchPreview() = SearchContent()

@Preview(name = "Watch", device = DS)
@Composable
private fun WatchPreview() = WatchContent()

@Preview(name = "Feeders", device = DS)
@Composable
private fun FeedersPreview() = FeederContent()
