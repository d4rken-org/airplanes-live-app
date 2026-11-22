package eu.darken.apl.screenshots

import android.location.Location
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.darken.apl.airport.core.AirportAggregation
import eu.darken.apl.airport.core.aggregateAirports
import eu.darken.apl.common.chart.ChartPoint
import eu.darken.apl.common.chart.ChartState
import eu.darken.apl.common.compose.preview.FakeAircraft
import eu.darken.apl.common.navigation.LocalNavigationController
import eu.darken.apl.common.navigation.NavigationController
import eu.darken.apl.common.theming.AplTheme
import eu.darken.apl.common.theming.ThemeMode
import eu.darken.apl.feeder.core.link.FeederLinkRepo
import eu.darken.apl.feeder.ui.FeederListScreen
import eu.darken.apl.feeder.ui.FeederListViewModel
import eu.darken.apl.feeder.ui.preview.mockMonitoredFeeder
import eu.darken.apl.main.core.ThemeState
import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.main.core.aircraft.currentLocation
import eu.darken.apl.main.core.aircraft.isLiveAt
import eu.darken.apl.main.ui.navigation.BottomNavBar
import eu.darken.apl.main.ui.navigation.MainTab
import eu.darken.apl.search.core.SearchInput
import eu.darken.apl.search.core.buildSearchQuery
import eu.darken.apl.search.ui.SearchScreen
import eu.darken.apl.search.ui.SearchViewModel
import eu.darken.apl.server.api.AirportSearchResultDto
import eu.darken.apl.server.api.AirportSummaryDto
import eu.darken.apl.server.api.FeederMonitorLatest
import eu.darken.apl.server.api.LinkedFeeder
import eu.darken.apl.watch.core.WatchId
import eu.darken.apl.watch.core.WatchLimit
import eu.darken.apl.watch.core.WatchSortMode
import eu.darken.apl.watch.core.db.types.AircraftWatchEntity
import eu.darken.apl.watch.core.db.types.BaseWatchEntity
import eu.darken.apl.watch.core.db.types.FlightWatchEntity
import eu.darken.apl.watch.core.db.types.LocationWatchEntity
import eu.darken.apl.watch.core.db.types.SquawkWatchEntity
import eu.darken.apl.watch.core.history.HourAircraft
import eu.darken.apl.watch.core.history.HourAircraftState
import eu.darken.apl.watch.core.history.HourCoverage
import eu.darken.apl.watch.core.history.LastSighting
import eu.darken.apl.watch.core.history.WatchActivityGrid
import eu.darken.apl.watch.core.history.WatchActivityTimeline
import eu.darken.apl.watch.core.history.WatchCheck
import eu.darken.apl.watch.core.history.WatchCountChartData
import eu.darken.apl.watch.core.makeWatchId
import eu.darken.apl.watch.core.server.WatchServerStatus
import eu.darken.apl.watch.core.types.AircraftWatch
import eu.darken.apl.watch.core.types.FlightWatch
import eu.darken.apl.watch.core.types.LocationWatch
import eu.darken.apl.watch.core.types.SquawkWatch
import eu.darken.apl.watch.core.types.Watch
import eu.darken.apl.watch.ui.LiveNow
import eu.darken.apl.watch.ui.WatchListScreen
import eu.darken.apl.watch.ui.WatchListViewModel
import eu.darken.apl.watch.ui.WatchServerLabel
import eu.darken.apl.watch.ui.details.WatchDetailsContent
import eu.darken.apl.watch.ui.details.WatchDetailsViewModel
import eu.darken.apl.watch.ui.lastSeenAt
import eu.darken.apl.watch.ui.sortedFor
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
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

private fun lufthansaJumbo(
    hex: String,
    registration: String,
    callsign: String,
    altitudeFt: Int,
    groundSpeed: Float,
    location: Location,
) = FakeAircraft(
    hex = hex,
    registration = registration,
    callsign = callsign,
    operator = "Lufthansa",
    airframe = "B748",
    description = "Boeing 747-830",
    squawk = "1000",
    altitudeFt = altitudeFt,
    groundSpeed = groundSpeed,
    location = location,
    messageSeenAt = fixedTime.minusSeconds(3),
    positionSeenAt = fixedTime.minusSeconds(3),
    fetchedAt = fixedTime,
)

private val frankfurtAirport = AirportSummaryDto(
    id = "oa:2212",
    name = "Frankfurt Main Airport",
    type = AirportSummaryDto.TYPE_LARGE,
    ident = "EDDF",
    latitude = 50.026706,
    longitude = 8.55835,
    municipality = "Frankfurt am Main",
    country = "DE",
    region = "DE-HE",
    icao = "EDDF",
    iata = "FRA",
    matchedCode = "EDDF",
    elevationFt = 364,
    scheduledService = true,
    website = "https://www.frankfurt-airport.de/",
    wikipedia = "https://en.wikipedia.org/wiki/Frankfurt_Airport",
    timeZone = "Europe/Berlin",
)

@Composable
internal fun SearchContent() {
    val input = SearchInput(text = "EDDF, DLH B748")
    val terms = buildSearchQuery(input).terms.map { it.text }
    // No airport is named like "DLH B748"
    val airports = aggregateAirports(
        terms = terms,
        results = listOf(
            AirportSearchResultDto(index = 0, status = AirportSearchResultDto.STATUS_ANSWERED, airports = listOf(frankfurtAirport)),
            AirportSearchResultDto(index = 1, status = AirportSearchResultDto.STATUS_ANSWERED),
        ),
    ) as AirportAggregation.Shown
    val results = listOf(
        lufthansaJumbo("3C4B34", "D-ABYT", "DLH400", 11_000, 310f, location(50.0920, 8.2240)),
        lufthansaJumbo("3C4B29", "D-ABYI", "DLH501", 14_500, 330f, location(49.2650, 7.9410)),
        lufthansaJumbo("3C4B2D", "D-ABYM", "DLH418", 36_000, 505f, location(54.1800, -14.6200)),
        lufthansaJumbo("3C4B30", "D-ABYP", "DLH717", 38_000, 540f, location(44.8900, 51.7300)),
    )
    ScreenshotWrapper {
        TabShell(tab = MainTab.SEARCH) {
            SearchScreen(
                state = SearchViewModel.State(
                    input = input,
                    items = listOf(
                        SearchViewModel.SearchItem.Airports(
                            SearchViewModel.AirportSection(
                                terms = terms,
                                cutoff = false,
                                status = SearchViewModel.AirportStatus.Results(airports),
                            )
                        ),
                        SearchViewModel.SearchItem.Summary(aircraftCount = results.size),
                    ) + results
                        .map {
                            SearchViewModel.SearchItem.AircraftResult(
                                aircraft = it,
                                watch = null,
                                distanceInMeter = it.distanceFromUs(),
                            )
                        }
                        .sortedBy { it.distanceInMeter ?: Float.MAX_VALUE },
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

private val HOUR: Duration = Duration.ofHours(1)

/** One cell per UTC hour from [from] through the current one, [countAt] is null for hours without history. */
private fun timeline(from: Instant, countAt: (Instant) -> Int?): WatchActivityTimeline {
    val start = WatchActivityTimeline.hourOf(from)
    val counts = generateSequence(start) { it.plus(HOUR) }.takeWhile { it <= fixedTime }.map(countAt).toList()
    return WatchActivityTimeline(
        from = start,
        cells = counts.map {
            when {
                it == null -> WatchActivityTimeline.Cell.UNKNOWN
                it > 0 -> WatchActivityTimeline.Cell.ACTIVE
                else -> WatchActivityTimeline.Cell.INACTIVE
            }
        },
        counts = counts.map { it ?: 0 },
    )
}

/** Nothing before the watch existed, nor yet for the hour that only just began. */
private fun recorded(watch: Watch, count: (Instant) -> Int): (Instant) -> Int? = { hour ->
    if (hour < WatchActivityTimeline.hourOf(watch.addedAt) || hour >= WatchActivityTimeline.hourOf(fixedTime)) null
    else count(hour)
}

private fun baseEntity(id: WatchId, type: String, age: Duration, notify: Boolean) = BaseWatchEntity(
    id = id,
    createdAt = fixedTime.minus(age),
    watchType = type,
    notificationEnabled = notify,
)

/** A watch the server holds and checks. */
private fun checked(watch: Watch, present: Boolean, matchedAt: Instant, matched: Collection<Aircraft>) = WatchServerStatus(
    isHeld = true,
    status = WatchServerStatus.Status.ACTIVE,
    pauseReason = null,
    rejection = null,
    presence = if (present) WatchServerStatus.Presence.PRESENT else WatchServerStatus.Presence.ABSENT,
    lastMatch = WatchCheck(
        watchId = watch.id,
        checkAt = matchedAt,
        aircraftCount = matched.size,
        seenHexes = matched.map { it.hex }.toSet(),
    ),
)

private val lastCheckAt = fixedTime.minusSeconds(30)

private val aircraftWatch = makeWatchId().let { id ->
    AircraftWatch(
        base = baseEntity(id, AircraftWatchEntity.TYPE_KEY, Duration.ofDays(20), notify = true),
        specific = AircraftWatchEntity(id = id, hexCode = mockAircraft1.hex, lastRegistration = mockAircraft1.registration),
    )
}
private val aircraftWatchStatus = checked(aircraftWatch, present = true, lastCheckAt, listOf(mockAircraft1)).let {
    AircraftWatch.Status(watch = aircraftWatch, lastHit = it.lastMatch, tracked = setOf(mockAircraft1), server = it)
}

/** Out by day, back overnight every other day. */
private fun aircraftWatchCount(hour: Instant): Int {
    val utc = hour.atZone(ZoneOffset.UTC)
    val outbound = ChronoUnit.DAYS.between(utc.toLocalDate(), fixedTime.atZone(ZoneOffset.UTC).toLocalDate()) % 2 == 0L
    return if (outbound == (utc.hour in 9..21)) 1 else 0
}

private val flightWatch = makeWatchId().let { id ->
    FlightWatch(
        base = baseEntity(id, FlightWatchEntity.TYPE_KEY, Duration.ofDays(30), notify = false),
        specific = FlightWatchEntity(id = id, callsign = "BAW902"),
    )
}
private val flightWatchLastSeen = Instant.parse("2026-01-15T08:52:00Z")
private val flightWatchStatus = checked(
    flightWatch,
    present = false,
    flightWatchLastSeen,
    listOf(FakeAircraft(hex = "406A3D", registration = "G-EUYR", callsign = "BAW902")),
).let { FlightWatch.Status(watch = flightWatch, lastHit = it.lastMatch, server = it) }

/** London to Frankfurt every morning. */
private fun flightWatchCount(hour: Instant): Int = if (hour.atZone(ZoneOffset.UTC).hour in 7..8) 1 else 0

private val squawkWatch = makeWatchId().let { id ->
    SquawkWatch(
        base = baseEntity(id, SquawkWatchEntity.TYPE_KEY, Duration.ofDays(40), notify = true),
        specific = SquawkWatchEntity(id = id, code = "7700"),
    )
}
private val squawkWatchTracked = setOf(
    mockAircraft2.copy(squawk = squawkWatch.code),
    mockAircraft3.copy(squawk = squawkWatch.code),
)
private val squawkWatchStatus = checked(squawkWatch, present = true, lastCheckAt, squawkWatchTracked).let {
    SquawkWatch.Status(watch = squawkWatch, lastHit = it.lastMatch, tracked = squawkWatchTracked, server = it)
}

private fun squawkWatchCount(hour: Instant): Int {
    val random = Random(hour.epochSecond)
    return if (random.nextInt(8) == 0) 1 + random.nextInt(2) else 0
}

// Bad Homburg, north of Frankfurt
private val homeWatch = makeWatchId().let { id ->
    LocationWatch(
        base = baseEntity(id, LocationWatchEntity.TYPE_KEY, Duration.ofDays(12), notify = true).copy(
            latitude = 50.2270,
            longitude = 8.6180,
            radius = 5_000f,
        ),
        specific = LocationWatchEntity(id = id, label = "Home"),
    )
}
private val homeTracked = setOf(
    FakeAircraft(
        hex = "3C49C3",
        registration = "D-ANRC",
        callsign = "CFG1504",
        operator = "Condor",
        airframe = "A21N",
        description = "Airbus A321neo",
        squawk = "1000",
        altitudeFt = 9_500,
        groundSpeed = 290f,
        location = location(50.2450, 8.6500),
        messageSeenAt = fixedTime.minusSeconds(1),
        positionSeenAt = fixedTime.minusSeconds(1),
        fetchedAt = fixedTime,
    ),
    FakeAircraft(
        hex = "3C6567",
        registration = "D-AINK",
        callsign = "DLH9EV",
        operator = "Lufthansa",
        airframe = "A20N",
        description = "Airbus A320neo",
        squawk = "1000",
        altitudeFt = 12_000,
        groundSpeed = 320f,
        location = location(50.2050, 8.5900),
        messageSeenAt = fixedTime.minusSeconds(2),
        positionSeenAt = fixedTime.minusSeconds(2),
        fetchedAt = fixedTime,
    ),
)
private val homeLastSighting = homeTracked.first().let {
    LastSighting(at = fixedTime.minusSeconds(40), hex = it.hex, callsign = it.callsign)
}
private val homeWatchStatus = checked(homeWatch, present = true, lastCheckAt, homeTracked).let {
    LocationWatch.Status(watch = homeWatch, lastHit = it.lastMatch, tracked = homeTracked, server = it)
}

/** Yesterday evening, the hour picked in the details' activity grid. */
private val homeSelectedHour = Instant.parse("2026-01-14T17:00:00Z")
private val homeHourAircraft = listOf(
    HourAircraft("3C6585", homeSelectedHour.plusSeconds(190), "DLH6TA", "D-AIZR", "A320", "Lufthansa"),
    HourAircraft("3C49C5", homeSelectedHour.plusSeconds(580), "CFG1311", "D-ANRE", "A21N", "Condor"),
    HourAircraft("4CAC4B", homeSelectedHour.plusSeconds(1_020), "RYR4KA", "EI-IHL", "B38M", "Ryanair"),
    HourAircraft("896471", homeSelectedHour.plusSeconds(1_440), "UAE45", "A6-EOE", "A388", "Emirates"),
    HourAircraft("406A3D", homeSelectedHour.plusSeconds(1_980), "BAW903", "G-EUYR", "A320", "British Airways"),
    HourAircraft("495291", homeSelectedHour.plusSeconds(2_460), "NJE751P", "CS-PHJ", "E55P", "NetJets Europe"),
    HourAircraft("3C6589", homeSelectedHour.plusSeconds(3_120), "DLH1KE", "D-AIBL", "A319", "Lufthansa"),
)

/** Aircraft per local hour on a usual day, none during the airport's night flight ban. */
private val homeUsualDay = intArrayOf(0, 0, 0, 0, 0, 1, 4, 8, 9, 7, 6, 6, 7, 6, 6, 7, 8, 9, 9, 8, 7, 5, 3, 0)

private fun homeCount(hour: Instant): Int {
    if (hour == homeSelectedHour) return homeHourAircraft.size
    val usual = homeUsualDay[hour.atZone(ZoneId.of("Europe/Berlin")).hour]
    if (usual == 0) return 0
    return (usual + Random(hour.epochSecond).nextInt(5) - 2).coerceAtLeast(0)
}

private val homeCounts = recorded(homeWatch, ::homeCount)

@Composable
internal fun WatchContent() {
    val window = fixedTime.minus(WatchListViewModel.ACTIVITY_WINDOW)
    val activity = mapOf(
        aircraftWatch.id to timeline(window, recorded(aircraftWatch, ::aircraftWatchCount)),
        flightWatch.id to timeline(window, recorded(flightWatch, ::flightWatchCount)),
        squawkWatch.id to timeline(window, recorded(squawkWatch, ::squawkWatchCount)),
        homeWatch.id to timeline(window, homeCounts),
    )
    val historyLastSeen = mapOf(
        aircraftWatch.id to lastCheckAt,
        flightWatch.id to flightWatchLastSeen,
        squawkWatch.id to lastCheckAt,
        homeWatch.id to homeLastSighting.at,
    )
    val statuses = listOf(aircraftWatchStatus, flightWatchStatus, squawkWatchStatus, homeWatchStatus)
    val items = statuses.sortedFor(WatchSortMode.BY_NOTE, activity, historyLastSeen).map { status ->
        val liveCount = status.tracked.count { it.isLiveAt(fixedTime) }
        val serverLabel = WatchServerLabel.of(status, isServerMode = true)
        when (status) {
            is AircraftWatch.Status, is FlightWatch.Status -> WatchListViewModel.WatchItem.Single(
                status = status,
                aircraft = status.tracked.firstOrNull(),
                ourLocation = ourLocation,
                activity = activity[status.id],
                historyLastSeenAt = historyLastSeen[status.id],
                liveCount = liveCount,
                serverLabel = serverLabel,
            )

            is SquawkWatch.Status -> WatchListViewModel.WatchItem.Multi(
                status = status,
                ourLocation = ourLocation,
                activity = activity[status.id],
                historyLastSeenAt = historyLastSeen[status.id],
                liveCount = liveCount,
                serverLabel = serverLabel,
            )

            is LocationWatch.Status -> WatchListViewModel.WatchItem.Multi(
                status = status,
                ourLocation = ourLocation,
                activity = activity[status.id],
                historyLastSeenAt = historyLastSeen[status.id],
                liveCount = liveCount,
                serverLabel = serverLabel,
                locationSummary = WatchListViewModel.LocationSummary.of(
                    lastSighting = homeLastSighting,
                    hourlySightingsInDay = activity[status.id]?.hourlySightingsInDay(fixedTime),
                ),
            )
        }
    }

    ScreenshotWrapper {
        TabShell(tab = MainTab.WATCH) {
            WatchListScreen(
                state = WatchListViewModel.State(
                    items = items,
                    now = fixedTime,
                    currentSortMode = WatchSortMode.BY_NOTE,
                    watchLimit = WatchLimit(count = items.size, max = null),
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

/** Past the map, down to the picked hour's aircraft. */
private val LOCATION_DETAILS_SCROLL = 280.dp

/** Shows content that scrolls itself as if scrolled [by] down, its scroll state is its own. */
private fun Modifier.scrolledBy(by: Dp) = clipToBounds().layout { measurable, constraints ->
    val offset = by.roundToPx()
    val height = constraints.maxHeight + offset
    val placeable = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, -offset) }
}

@Composable
internal fun LocationWatchContent() {
    val zone = ZoneId.systemDefault()
    val grid = WatchActivityGrid.build(timeline(WatchActivityGrid.startOf(fixedTime, zone), homeCounts), zone, fixedTime)
    val countPoints = generateSequence(WatchActivityTimeline.hourOf(fixedTime.minus(Duration.ofDays(30)))) { it.plus(HOUR) }
        .takeWhile { it <= fixedTime }
        .mapNotNull { hour -> homeCounts(hour)?.let { ChartPoint(hour, it.toDouble()) } }
        .toList()
    val state = WatchDetailsViewModel.State(
        status = homeWatchStatus,
        now = fixedTime,
        aircraft = null,
        registration = null,
        liveCount = homeTracked.count { it.isLiveAt(fixedTime) },
        live = LiveNow(homeTracked.size),
        distanceInMeter = null,
        lastSeenAt = homeWatchStatus.lastSeenAt(homeLastSighting.at),
        matches = WatchDetailsViewModel.Matches(
            aircraft = homeTracked
                .map { WatchDetailsViewModel.Match(it, it.currentLocation?.let { at -> homeWatch.center.distanceTo(at) }) }
                .sortedWith(compareBy(nullsLast()) { it.distanceInMeter }),
            checkAt = fixedTime.minus(Duration.ofMinutes(1)),
            total = homeTracked.size,
            fromWatchCenter = true,
            sortedByDistance = true,
        ),
        activityState = ChartState.Ready(grid),
        countState = ChartState.Ready(WatchCountChartData(countPoints)),
        hourSelection = WatchDetailsViewModel.HourSelection(
            range = WatchDetailsViewModel.HourSelection.rangeAt(fixedTime),
            selected = listOf(homeSelectedHour),
            selectedSlot = grid.days.firstNotNullOfOrNull { day -> day.slots.firstOrNull { homeSelectedHour in it.hours } },
            aircraft = HourAircraftState.Complete(
                aircraft = homeHourAircraft.sortedBy { it.lastSeenAt },
                provisional = false,
                coverage = HourCoverage.FULL,
            ),
        ),
    )

    ScreenshotWrapper {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val maxPhotoHeight = with(LocalDensity.current) { (maxHeight * 0.45f).roundToPx() }
            Surface(color = BottomSheetDefaults.ContainerColor, modifier = Modifier.fillMaxSize()) {
                WatchDetailsContent(
                    state = state,
                    progress = { 1f },
                    maxPhotoHeight = maxPhotoHeight,
                    onPhotoClick = {},
                    onNoteChanged = {},
                    onNotificationsChanged = {},
                    onShowOnMap = {},
                    onShowInSearch = {},
                    onRemove = {},
                    onThumbnailClick = {},
                    onAircraftClick = {},
                    onLocationSaved = { _, _, _, _ -> },
                    onExpand = {},
                    modifier = Modifier.scrolledBy(LOCATION_DETAILS_SCROLL),
                )
            }
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
            feeder = mockMonitoredFeeder(
                label = "Home Receiver",
                id = "abc12",
                lastConnectedAt = now.minusSeconds(60),
                latest = FeederMonitorLatest(
                    beastClients = 1,
                    msgsPerSecond = 612.4,
                    positionsPerSecond = 41.7,
                    avgKbitPerSecond = 93.8,
                    connTime = 1_209_600,
                    positions = 148_210,
                    mlatClients = 1,
                    mlatMessageRate = 18.6,
                    mlatPeers = 27,
                    mlatBadSyncTimeout = 0.0,
                    mlatOutlierPercent = 1.3,
                ),
            ),
            isOffline = false,
            beastSparkline = weekOfMessageRates(now, currentRate = 612.4, seed = 1),
        ),
        FeederListViewModel.FeederItem(
            feeder = mockMonitoredFeeder(
                label = "Office Receiver",
                id = "def34",
                lastConnectedAt = now.minusSeconds(75),
                latest = FeederMonitorLatest(
                    beastClients = 1,
                    msgsPerSecond = 284.9,
                    positionsPerSecond = 19.2,
                    avgKbitPerSecond = 44.1,
                    connTime = 432_000,
                    positions = 67_455,
                    mlatClients = 1,
                    mlatMessageRate = 7.2,
                    mlatPeers = 12,
                    mlatBadSyncTimeout = 0.0,
                    mlatOutlierPercent = 2.8,
                ),
            ),
            isOffline = false,
            beastSparkline = weekOfMessageRates(now, currentRate = 284.9, seed = 2),
        ),
        FeederListViewModel.FeederItem(
            feeder = mockMonitoredFeeder(
                label = "Remote Station",
                id = "ghi56",
                online = false,
                lastConnectedAt = now.minus(Duration.ofDays(3)),
                latest = FeederMonitorLatest(
                    beastClients = 1,
                    msgsPerSecond = 153.2,
                    positionsPerSecond = 8.4,
                    avgKbitPerSecond = 23.6,
                    connTime = 259_200,
                    positions = 29_310,
                    mlatClients = 1,
                    mlatMessageRate = 3.1,
                    mlatPeers = 6,
                    mlatBadSyncTimeout = 0.0,
                    mlatOutlierPercent = 4.5,
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

@Preview(name = "Location watch", device = DS)
@Composable
private fun LocationWatchPreview() = LocationWatchContent()

@Preview(name = "Feeders", device = DS)
@Composable
private fun FeedersPreview() = FeederContent()
