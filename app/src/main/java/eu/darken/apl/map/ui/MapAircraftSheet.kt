package eu.darken.apl.map.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.twotone.CenterFocusStrong
import androidx.compose.material.icons.twotone.CenterFocusWeak
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material.icons.twotone.ContentCopy
import androidx.compose.material.icons.twotone.History
import androidx.compose.material.icons.twotone.Map
import androidx.compose.material.icons.twotone.Navigation
import androidx.compose.material.icons.twotone.Search
import androidx.compose.material.icons.twotone.Share
import androidx.compose.material.icons.twotone.Visibility
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.MultiContentMeasurePolicy
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import eu.darken.apl.R
import eu.darken.apl.common.compose.GlanceMetric
import eu.darken.apl.common.compose.Preview2
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.common.compose.SHEET_GLANCE_TEXT_STAGE_END
import eu.darken.apl.common.compose.SeenPill
import eu.darken.apl.common.compose.SheetAction
import eu.darken.apl.common.compose.SheetGlanceDefaults
import eu.darken.apl.common.compose.SheetGlanceLayout
import eu.darken.apl.common.compose.StatusPill
import eu.darken.apl.common.compose.rememberSheetOpeningDrag
import eu.darken.apl.common.compose.sheetGlanceMediaStage
import eu.darken.apl.common.flight.Airport
import eu.darken.apl.common.flight.FlightRoute
import eu.darken.apl.common.flight.HeightAboveAirport
import eu.darken.apl.common.flight.RouteProgress
import eu.darken.apl.common.flight.heightAboveField
import eu.darken.apl.common.flight.ui.GlanceRoute
import eu.darken.apl.common.flight.ui.clickLabel
import eu.darken.apl.common.planespotters.PlanespottersCreditHeight
import eu.darken.apl.common.planespotters.PlanespottersThumbnail
import eu.darken.apl.common.planespotters.coil.AircraftThumbnailQuery
import eu.darken.apl.common.units.LocalUnitFormatter
import eu.darken.apl.common.units.altitudeText
import eu.darken.apl.common.units.distanceText
import eu.darken.apl.common.units.speedText
import eu.darken.apl.common.units.text
import eu.darken.apl.main.core.aircraft.AircraftSourceType
import eu.darken.apl.main.core.aircraft.SquawkEmergency
import eu.darken.apl.map.core.MapAircraftDetails
import eu.darken.apl.map.core.MapWatches
import eu.darken.apl.watch.core.WatchId
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * [progress] runs from 0 (collapsed) to 1 (expanded) and is only read during layout and drawing.
 * [onCollapsedHeight] reports the height of the glance item at progress 0.
 */
@Composable
internal fun AircraftDetailsSheetContent(
    details: MapAircraftDetails,
    loading: Boolean,
    /** Why the details stay partial, instead of waiting for them. */
    note: String?,
    route: FlightRoute?,
    routeProgress: RouteProgress,
    progress: () -> Float,
    topInset: Int,
    maxPhotoHeight: Int,
    onCollapsedHeight: (Int) -> Unit,
    watched: Boolean,
    seen: NativeMapViewModel.Seen?,
    /** Server time, what [NativeMapViewModel.Seen.At] is measured against. */
    serverNow: () -> Long,
    /** The first labelled action: Show in search on the map, Show on map everywhere else. */
    placeAction: AircraftPlaceAction,
    onWatchAction: (String) -> Unit,
    onWatchWithNote: (String) -> Unit,
    onPhotoClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    distanceInMeter: Float? = null,
    watchMatches: List<MapWatches.Match> = emptyList(),
    onOpenWatch: (WatchId) -> Unit = {},
    onAirportClick: ((Airport) -> Unit)? = null,
    /** Searches for the airframe type, which isn't a link without it. */
    onTypeClick: ((String) -> Unit)? = null,
    /** Copies a value, without it the values offer no copying. */
    onCopy: ((String) -> Unit)? = null,
    listState: LazyListState = rememberLazyListState(),
    heightAboveAirport: HeightAboveAirport? = null,
) {
    val openingDrag = rememberSheetOpeningDrag(progress)

    LazyColumn(
        state = listState,
        // Full height keeps the expanded sheet at the top, which progress is measured against
        modifier = modifier
            .fillMaxSize()
            .layout { measurable, constraints ->
                val inset = (topInset * progress().coerceIn(0f, 1f)).roundToInt()
                val placeable = measurable.measure(constraints.offset(vertical = -inset))
                layout(placeable.width, placeable.height + inset) { placeable.place(0, inset) }
            }
            .nestedScroll(openingDrag),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item(key = "glance") {
            AircraftGlance(
                details = details,
                loading = loading,
                note = note,
                route = route,
                routeProgress = routeProgress,
                progress = progress,
                maxPhotoHeight = maxPhotoHeight,
                onCollapsedHeight = onCollapsedHeight,
                onPhotoClick = onPhotoClick,
                watched = watched,
                onWatchAction = { onWatchAction(details.hex) },
                seen = seen,
                serverNow = serverNow,
                onAirportClick = onAirportClick,
            )
        }

        item(key = "details") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when (placeAction) {
                        is AircraftPlaceAction.ShowInSearch -> SheetAction(
                            icon = Icons.TwoTone.Search,
                            label = stringResource(R.string.common_show_in_search),
                            onClick = { placeAction.onClick(details.hex) },
                            modifier = Modifier.weight(1f),
                        )

                        is AircraftPlaceAction.ShowOnMap -> SheetAction(
                            icon = Icons.TwoTone.Map,
                            label = stringResource(R.string.common_show_on_map_action),
                            onClick = { placeAction.onClick(details.hex) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    SheetAction(
                        icon = Icons.TwoTone.Visibility,
                        label = stringResource(watchActionLabel(watched)),
                        onClick = { onWatchWithNote(details.hex) },
                        modifier = Modifier.weight(1f),
                    )
                }

                // Callsign and squawk watches it matches, the watch button above only covers its hex
                if (watchMatches.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        watchMatches.forEach { match ->
                            AssistChip(
                                onClick = { onOpenWatch(match.id) },
                                label = {
                                    val label = when (match.kind) {
                                        MapWatches.Match.Kind.CALLSIGN -> R.string.map_aircraft_watch_match_callsign
                                        MapWatches.Match.Kind.SQUAWK -> R.string.map_aircraft_watch_match_squawk
                                    }
                                    Text(stringResource(label, match.value))
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.Visibility,
                                        contentDescription = null,
                                        modifier = Modifier.size(AssistChipDefaults.IconSize),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                },
                            )
                        }
                    }
                }

                DetailSection(
                    header = stringResource(R.string.map_aircraft_details_section_route),
                    onCopy = onCopy,
                    pairs = DetailPairs(
                        listOfNotNull(
                            route?.origin?.let {
                                DetailPair(
                                    stringResource(R.string.map_aircraft_details_route_from),
                                    it.fullLabel(),
                                    wide = true,
                                    link = it.detailLink(onAirportClick),
                                )
                            },
                            route?.destination?.let {
                                DetailPair(
                                    stringResource(R.string.map_aircraft_details_route_to),
                                    it.fullLabel(),
                                    wide = true,
                                    link = it.detailLink(onAirportClick),
                                )
                            },
                        ),
                    ),
                )

                DetailSection(
                    header = stringResource(R.string.map_aircraft_details_section_aircraft),
                    onCopy = onCopy,
                    pairs = DetailPairs(
                        listOfNotNull(
                            details.registration?.let {
                                DetailPair(
                                    stringResource(R.string.common_registration_label),
                                    it,
                                    copyLabel = stringResource(R.string.map_aircraft_details_copy_registration),
                                )
                            },
                            DetailPair(
                                stringResource(R.string.common_hex_label),
                                details.hex,
                                mono = true,
                                copyLabel = stringResource(R.string.map_aircraft_details_copy_hex),
                            ),
                            details.icaoType?.let { type ->
                                DetailPair(
                                    stringResource(R.string.common_airframe_label),
                                    type,
                                    link = onTypeClick?.takeIf { type.isNotBlank() }?.let { onClick ->
                                        DetailLink(stringResource(R.string.map_aircraft_details_search_type_x, type)) { onClick(type) }
                                    },
                                )
                            },
                            details.operator?.let { DetailPair(stringResource(R.string.map_aircraft_details_operator_label), it) },
                            details.country?.let { DetailPair(stringResource(R.string.map_aircraft_details_country_label), it) },
                            listOfNotNull(
                                stringResource(R.string.map_aircraft_details_flag_military).takeIf { details.military },
                                stringResource(R.string.map_aircraft_details_flag_ladd).takeIf { details.ladd },
                                stringResource(R.string.map_aircraft_details_flag_pia).takeIf { details.pia },
                            ).takeIf { it.isNotEmpty() }?.let {
                                DetailPair(
                                    stringResource(R.string.map_aircraft_details_flags_label),
                                    it.joinToString(stringResource(R.string.common_list_separator)),
                                )
                            },
                        ),
                    ),
                )

                val units = LocalUnitFormatter.current
                DetailSection(
                    header = stringResource(R.string.map_aircraft_details_section_flight_data),
                    onCopy = onCopy,
                    pairs = DetailPairs(
                        listOfNotNull(
                            // On a row of its own, the pairs after it still share theirs
                            details.callsign?.let {
                                DetailPair(
                                    stringResource(R.string.map_aircraft_details_callsign_label),
                                    it,
                                    wide = true,
                                    copyLabel = stringResource(R.string.map_aircraft_details_copy_callsign),
                                )
                            },
                            altitudeText(details.altitudeFt, details.onGround)?.let {
                                DetailPair(stringResource(R.string.common_altitude_label), it)
                            },
                            details.altitudeGeomFt?.let {
                                DetailPair(stringResource(R.string.map_aircraft_details_altitude_gps_label), units.altitude(it).text())
                            },
                            // Takes a row of its own, after the altitudes so they still share one
                            heightAboveAirport?.let {
                                DetailPair(
                                    stringResource(R.string.map_aircraft_details_height_above_label),
                                    if (it.aboutFieldLevel) {
                                        stringResource(
                                            R.string.map_aircraft_details_height_above_field_level_x,
                                            it.airport.displayLabel,
                                        )
                                    } else {
                                        stringResource(
                                            R.string.map_aircraft_details_height_above_value_x_y,
                                            units.heightAboveField(it.heightFt).text(),
                                            it.airport.displayLabel,
                                        )
                                    },
                                    wide = true,
                                )
                            },
                            details.vertRateFpm?.let {
                                DetailPair(stringResource(R.string.map_aircraft_details_vert_rate_label), units.verticalRate(it).text())
                            },
                            details.squawk?.let {
                                DetailPair(
                                    stringResource(R.string.common_squawk_label),
                                    it,
                                    mono = true,
                                    alert = SquawkEmergency.fromSquawk(it) != null,
                                )
                            },
                            speedText(details.groundSpeedKt)?.let { DetailPair(stringResource(R.string.common_speed_label), it) },
                            speedText(details.iasKt)?.let { DetailPair(stringResource(R.string.map_aircraft_details_ias_label), it) },
                            details.trackDegrees?.let {
                                DetailPair(stringResource(R.string.map_aircraft_details_track_label), units.degrees(it.toDouble()).text())
                            },
                            details.headingDegrees?.let {
                                DetailPair(stringResource(R.string.map_aircraft_details_heading_label), units.degrees(it).text())
                            },
                            details.tempC?.let {
                                DetailPair(stringResource(R.string.map_aircraft_details_temp_label), units.temperature(it).text())
                            },
                        ),
                    ),
                )

                DetailSection(
                    header = stringResource(R.string.map_aircraft_details_section_position),
                    onCopy = onCopy,
                    pairs = DetailPairs(
                        listOfNotNull(
                            details.position?.let {
                                DetailPair(stringResource(R.string.map_aircraft_details_position_label), it, wide = true)
                            },
                            details.sourceType?.let {
                                DetailPair(stringResource(R.string.map_aircraft_details_source_label), stringResource(it.labelRes))
                            },
                            distanceText(distanceInMeter)?.let {
                                DetailPair(
                                    stringResource(R.string.common_distance_label),
                                    stringResource(R.string.general_xdistance_away_label, it),
                                )
                            },
                        ),
                    ),
                )
            }
        }
    }
}

internal sealed interface AircraftPlaceAction {
    val onClick: (String) -> Unit

    data class ShowInSearch(override val onClick: (String) -> Unit) : AircraftPlaceAction
    data class ShowOnMap(override val onClick: (String) -> Unit) : AircraftPlaceAction
}

internal const val AIRCRAFT_SHEET_PHOTO_TAG = "aircraft_sheet_photo"
internal const val AIRCRAFT_SHEET_HEADLINE_TAG = "aircraft_sheet_headline"

private val GLANCE_ACTION_SIZE = 48.dp
private val GLANCE_ICON_SIZE = 20.dp

private fun watchActionLabel(watched: Boolean) =
    if (watched) R.string.watch_list_watch_edit_label else R.string.watch_list_watch_add_label

@Composable
private fun AircraftGlance(
    details: MapAircraftDetails,
    loading: Boolean,
    note: String?,
    route: FlightRoute?,
    routeProgress: RouteProgress = RouteProgress.Unknown,
    progress: () -> Float,
    onCollapsedHeight: (Int) -> Unit,
    onPhotoClick: (String) -> Unit,
    maxPhotoHeight: Int = 0,
    watched: Boolean = false,
    onWatchAction: () -> Unit = {},
    seen: NativeMapViewModel.Seen? = null,
    serverNow: () -> Long = System::currentTimeMillis,
    onAirportClick: ((Airport) -> Unit)? = null,
) {
    val currentProgress by rememberUpdatedState(progress)
    // Gone once the text has dropped below the photo, the labelled actions below the glance take over
    val actionFade = { (currentProgress() / SHEET_GLANCE_TEXT_STAGE_END).coerceIn(0f, 1f) }
    val actionShown by remember { derivedStateOf { actionFade() < 1f } }
    val headline = details.callsign ?: details.registration ?: details.hex
    val headlineLineHeight = MaterialTheme.typography.titleMedium.lineHeight
    var photoQuery by remember(details.hex) {
        mutableStateOf(AircraftThumbnailQuery(hex = details.hex, large = true))
    }
    var hexFoundNoPhoto by remember(details.hex) { mutableStateOf(false) }
    LaunchedEffect(hexFoundNoPhoto, details.registration) {
        val registration = details.registration
        if (hexFoundNoPhoto && registration != null && photoQuery.registration == null) {
            photoQuery = photoQuery.copy(registration = registration)
        }
    }

    SheetGlanceLayout(
        progress = progress,
        maxMediaHeight = maxPhotoHeight,
        onCollapsedHeight = onCollapsedHeight,
        actionCenter = with(LocalDensity.current) { headlineLineHeight.toDp() } / 2,
        media = {
            Box(modifier = Modifier.testTag(AIRCRAFT_SHEET_PHOTO_TAG)) {
                PlanespottersThumbnail(
                    query = photoQuery,
                    shape = RectangleShape,
                    aspectRatio = null,
                    onImageClick = { meta -> onPhotoClick(meta.link) },
                    creditSpread = { sheetGlanceMediaStage(currentProgress()) },
                    onNoPhoto = { if (photoQuery.registration == null) hexFoundNoPhoto = true },
                    modifier = Modifier.fillMaxSize(),
                )
                // Above the photo credit, the top corners are under the sheet's rounding and the floating actions
                val creditHeight = with(LocalDensity.current) { PlanespottersCreditHeight.toDp() }
                if (details.military) {
                    Image(
                        painter = painterResource(R.drawable.ic_map_military_badge),
                        contentDescription = stringResource(R.string.map_aircraft_details_flag_military),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 6.dp, bottom = creditHeight + 6.dp)
                            .size(20.dp),
                    )
                }
            }
        },
        identity = {
            GlanceIdentity(
                headline = headline,
                type = details.typeLong ?: details.icaoType,
                loading = loading,
                note = note,
                seen = seen,
                serverNow = serverNow,
                // The headline may run under the button's empty touch area, up to 8dp before its icon
                headlineEndReserve = {
                    val iconInset = (GLANCE_ACTION_SIZE - GLANCE_ICON_SIZE) / 2
                    (SheetGlanceDefaults.ActionEnd + GLANCE_ACTION_SIZE - iconInset + 8.dp - SheetGlanceDefaults.PadH) *
                        (1f - actionFade())
                },
            )
        },
        secondary = {
            details.operator?.let { GlanceOwnerLine(it) }
            listOfNotNull(details.registration.takeIf { it != headline }, details.country)
                .takeIf { it.isNotEmpty() }
                ?.let { GlanceOwnerLine(it.joinToString(" \u00b7 ")) }
        },
        status = {
            GlanceStatus(details = details, route = route, routeProgress = routeProgress, onAirportClick = onAirportClick)
        },
        action = {
            if (actionShown) {
                GlanceWatchAction(
                    watched = watched,
                    onClick = onWatchAction,
                    modifier = Modifier.graphicsLayer { alpha = 1f - actionFade() },
                )
            }
        },
    )
}

@Composable
private fun GlanceIdentity(
    headline: String,
    type: String?,
    loading: Boolean,
    note: String?,
    seen: NativeMapViewModel.Seen?,
    serverNow: () -> Long,
    headlineEndReserve: () -> Dp = { 0.dp },
) {
    Column {
        Layout(
            contents = listOf<@Composable () -> Unit>(
                {
                    Text(
                        text = headline,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                },
                { if (seen != null) SeenBadge(seen = seen, serverNow = serverNow) },
            ),
            modifier = Modifier
                .testTag(AIRCRAFT_SHEET_HEADLINE_TAG)
                .layout { measurable, constraints ->
                    val maxWidth = (constraints.maxWidth - headlineEndReserve().roundToPx()).coerceAtLeast(0)
                    val placeable = measurable.measure(
                        constraints.copy(minWidth = min(constraints.minWidth, maxWidth), maxWidth = maxWidth)
                    )
                    layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
                },
            measurePolicy = HeadlineRowMeasurePolicy,
        )
        if (type != null) {
            Text(
                text = type,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (note != null) {
            Text(
                text = note,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SeenBadge(
    seen: NativeMapViewModel.Seen,
    serverNow: () -> Long,
    modifier: Modifier = Modifier,
) {
    when (seen) {
        NativeMapViewModel.Seen.Unknown -> StatusPill(
            text = stringResource(R.string.map_aircraft_details_seen_unknown),
            highlighted = false,
            modifier = modifier,
            description = stringResource(R.string.aircraft_details_last_seen_unknown),
        )

        else -> SeenPill(
            seenAt = (seen as? NativeMapViewModel.Seen.At)?.millis,
            serverNow = serverNow,
            modifier = modifier,
            isLive = { seen == NativeMapViewModel.Seen.Live },
        )
    }
}

private val HEADLINE_ROW_GAP = 8.dp
private const val HEADLINE_PILL_MAX_SHARE = 0.6f

/**
 * Headline, spinner and pill in one line. A Row would measure the pill before the headline with all of the width.
 * Capped at a share of the line, a long age still leaves the headline room.
 */
private object HeadlineRowMeasurePolicy : MultiContentMeasurePolicy {
    override fun MeasureScope.measure(
        measurables: List<List<Measurable>>,
        constraints: Constraints,
    ): MeasureResult {
        val (headlines, spinners, pills) = measurables
        val loose = Constraints(maxHeight = constraints.maxHeight)
        val gap = HEADLINE_ROW_GAP.roundToPx()
        val gaps = gap * (measurables.sumOf { it.size } - 1).coerceAtLeast(0)
        val bounded = constraints.hasBoundedWidth
        val spinner = spinners.firstOrNull()?.measure(loose)
        val spinnerWidth = spinner?.width ?: 0
        val pill = pills.firstOrNull()?.let {
            val maxWidth = when {
                bounded -> min(
                    (constraints.maxWidth * HEADLINE_PILL_MAX_SHARE).toInt(),
                    constraints.maxWidth - gaps - spinnerWidth,
                ).coerceAtLeast(0)

                else -> Constraints.Infinity
            }
            it.measure(loose.copy(maxWidth = maxWidth))
        }
        val headlineWidth = when {
            bounded -> (constraints.maxWidth - gaps - spinnerWidth - (pill?.width ?: 0)).coerceAtLeast(0)
            else -> Constraints.Infinity
        }
        val headline = headlines.firstOrNull()?.measure(loose.copy(maxWidth = headlineWidth))
        val placeables = listOfNotNull(headline, spinner, pill)
        val width = constraints.constrainWidth(placeables.sumOf { it.width } + gaps)
        val height = constraints.constrainHeight(placeables.maxOfOrNull { it.height } ?: 0)
        return layout(width, height) {
            var x = 0
            placeables.forEach { placeable ->
                placeable.placeRelative(x, (height - placeable.height) / 2)
                x += placeable.width + gap
            }
        }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurables: List<List<IntrinsicMeasurable>>,
        height: Int,
    ): Int = intrinsicWidth(measurables) { it.minIntrinsicWidth(height) }

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurables: List<List<IntrinsicMeasurable>>,
        height: Int,
    ): Int = intrinsicWidth(measurables) { it.maxIntrinsicWidth(height) }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurables: List<List<IntrinsicMeasurable>>,
        width: Int,
    ): Int = measurables.flatten().maxOfOrNull { it.minIntrinsicHeight(width) } ?: 0

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurables: List<List<IntrinsicMeasurable>>,
        width: Int,
    ): Int = measurables.flatten().maxOfOrNull { it.maxIntrinsicHeight(width) } ?: 0

    private fun IntrinsicMeasureScope.intrinsicWidth(
        measurables: List<List<IntrinsicMeasurable>>,
        childWidth: (IntrinsicMeasurable) -> Int,
    ): Int {
        val children = measurables.flatten()
        return children.sumOf(childWidth) + HEADLINE_ROW_GAP.roundToPx() * (children.size - 1).coerceAtLeast(0)
    }
}

@Composable
private fun GlanceWatchAction(
    watched: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier.size(GLANCE_ACTION_SIZE)) {
        Icon(
            if (watched) Icons.Filled.Visibility else Icons.TwoTone.Visibility,
            contentDescription = stringResource(watchActionLabel(watched)),
            tint = if (watched) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(GLANCE_ICON_SIZE),
        )
    }
}

@Composable
private fun GlanceOwnerLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
internal fun GlanceStatus(
    details: MapAircraftDetails,
    route: FlightRoute?,
    routeProgress: RouteProgress,
    onAirportClick: ((Airport) -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val emergency = SquawkEmergency.fromSquawk(details.squawk)
        if (emergency != null) SquawkBanner(squawk = details.squawk!!, emergency = emergency)
        if (route != null && (route.origin != null || route.destination != null)) {
            GlanceRoute(route = route, progress = routeProgress, onAirportClick = onAirportClick)
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            GlanceMetric(
                value = altitudeText(details.altitudeFt, details.onGround),
                label = stringResource(R.string.common_altitude_label),
                modifier = Modifier.weight(1f),
            )
            GlanceMetric(
                value = details.vertRateFpm?.takeIf { !details.onGround }?.let {
                    LocalUnitFormatter.current.verticalRateShort(it).text()
                },
                label = stringResource(R.string.map_aircraft_details_vs_label),
                modifier = Modifier.weight(1f),
            )
            GlanceMetric(
                value = speedText(details.groundSpeedKt),
                label = stringResource(R.string.common_speed_label),
                modifier = Modifier.weight(1f),
            )
            GlanceMetric(
                value = details.trackDegrees?.let { LocalUnitFormatter.current.degrees(it.toDouble()).text() },
                label = stringResource(R.string.map_aircraft_details_track_label),
                modifier = Modifier.weight(1f),
            ) {
                val degrees = details.trackDegrees ?: return@GlanceMetric
                Icon(
                    Icons.TwoTone.Navigation,
                    contentDescription = null,
                    modifier = Modifier
                        .size(16.dp)
                        .rotate(degrees),
                )
            }
        }
    }
}

/**
 * History floats above the sheet's top start, follow or center, share and close above its top end.
 * [sheetTop] is the sheet's offset in this layout's coordinates; once the sheet rises too far they stay
 * at [safeTop], over the grown photo, and scroll away with it by [contentScroll]. Share is labelled only
 * on the expanded sheet, where history, follow and center are gone. Where both sides do not fit, follow
 * or center and then history drop their labels.
 */
@Composable
internal fun SheetFloatingActions(
    sheetTop: () -> Float?,
    contentScroll: () -> Int,
    progress: () -> Float,
    safeTop: Int,
    sheetMaxWidth: Int,
    startInset: Int,
    endInset: Int,
    onRowHeight: (Int) -> Unit,
    /** Null when there is nothing to follow. */
    follow: Boolean?,
    onToggleFollow: () -> Unit,
    /** Null when there is nothing to share. */
    onShare: (() -> Unit)?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /** Null when there is no history to offer. */
    history: NativeMapViewModel.HistoryAction? = null,
    onHistory: () -> Unit = {},
    /** Null when there is nothing to center on. */
    onCenter: (() -> Unit)? = null,
) {
    val currentProgress by rememberUpdatedState(progress)
    val currentOnRowHeight by rememberUpdatedState(onRowHeight)
    // History, follow and center fade out while the sheet expands, an expanded sheet leaves no map to act on
    val mapFade = { ((currentProgress() - 0.6f) / 0.35f).coerceIn(0f, 1f) }
    val mapHidden by remember { derivedStateOf { mapFade() >= 1f } }

    @Composable
    fun HistoryChip(compact: Boolean) {
        if (!mapHidden && history != null) {
            val locked = history == NativeMapViewModel.HistoryAction.LOCKED
            FloatingChip(
                onClick = onHistory,
                enabled = history != NativeMapViewModel.HistoryAction.LOADING,
                compact = compact && !locked,
                modifier = Modifier.fading(mapFade),
            ) {
                val label = stringResource(R.string.map_history_action)
                if (history == NativeMapViewModel.HistoryAction.LOADING) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(18.dp)
                            .semantics { if (compact) contentDescription = label },
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        Icons.TwoTone.History,
                        contentDescription = if (compact) label else null,
                        modifier = Modifier.size(18.dp),
                    )
                }
                if (!compact) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                if (locked) HistoryProBadge()
            }
        }
    }

    @Composable
    fun ActionsRow(compact: Boolean, slotFade: () -> Float = mapFade) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!mapHidden && follow != null) {
                FollowToggle(
                    follow = follow,
                    compact = compact,
                    onToggle = onToggleFollow,
                    modifier = Modifier.fadingSlot(slotFade),
                )
            }
            if (!mapHidden && onCenter != null) {
                val label = stringResource(R.string.map_airport_center_action)
                FloatingChip(
                    onClick = onCenter,
                    compact = compact,
                    // The close button next to it brings its own 8dp gap, airports have nothing to share
                    modifier = Modifier.fadingSlot(slotFade, gap = 0.dp),
                ) {
                    Icon(
                        Icons.TwoTone.CenterFocusStrong,
                        contentDescription = if (compact) label else null,
                        modifier = Modifier.size(18.dp),
                    )
                    if (!compact) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
            val shareCompact = compact || !mapHidden
            if (onShare != null) {
                FloatingChip(onClick = onShare, compact = shareCompact) {
                    Icon(
                        Icons.TwoTone.Share,
                        contentDescription = if (shareCompact) stringResource(R.string.common_share_action) else null,
                        modifier = Modifier.size(18.dp),
                    )
                    if (!shareCompact) {
                        Text(
                            text = stringResource(R.string.common_share_action),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
            Surface(
                onClick = onClose,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(36.dp),
            ) {
                Icon(
                    Icons.TwoTone.Close,
                    contentDescription = stringResource(R.string.common_close_action),
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
    }

    SubcomposeLayout(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds(),
    ) { constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val margin = 12.dp.roundToPx()
        val gap = 8.dp.roundToPx()
        val sheetWidth = min(constraints.maxWidth, sheetMaxWidth)
        val start = max((constraints.maxWidth - sheetWidth) / 2, startInset) + margin
        val end = min((constraints.maxWidth + sheetWidth) / 2, constraints.maxWidth - endInset) - margin
        var historyChip = subcompose("history") { HistoryChip(compact = false) }.firstOrNull()?.measure(loose)
        val historySpace = historyChip?.let { it.width + gap } ?: 0
        // Sized at rest: follow's slot narrows while it fades, which must not bring the labels back
        val restWidth = { compact: Boolean ->
            subcompose(if (compact) "compactRest" else "fullRest") { ActionsRow(compact, slotFade = { 0f }) }
                .first().measure(loose).width
        }
        val compactActions = historySpace + restWidth(false) > end - start
        if (compactActions && historySpace + restWidth(true) > end - start) {
            historyChip = subcompose("historyCompact") { HistoryChip(compact = true) }.firstOrNull()?.measure(loose)
        }
        val row = subcompose(if (compactActions) "compact" else "full") { ActionsRow(compactActions) }.first().measure(loose)
        val height = max(row.height, historyChip?.height ?: 0)
        currentOnRowHeight(height)
        layout(constraints.maxWidth, constraints.maxHeight) {
            val top = sheetTop() ?: return@layout
            val y = max(top.roundToInt() - height - gap, safeTop + gap) - contentScroll()
            historyChip?.let { it.placeRelative(start, y + (height - it.height) / 2) }
            row.placeRelative(end - row.width, y + (height - row.height) / 2)
        }
    }
}

@Composable
internal fun FollowToggle(
    follow: Boolean,
    compact: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        checked = follow,
        onCheckedChange = { onToggle() },
        shape = CircleShape,
        color = if (follow) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (follow) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        shadowElevation = 4.dp,
        modifier = modifier,
    ) {
        val label = stringResource(if (follow) R.string.map_control_unfollow else R.string.map_control_follow)
        Row(
            modifier = if (compact) {
                Modifier.padding(8.dp)
            } else {
                Modifier.padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
            },
            verticalAlignment = Alignment.CenterVertically,
            // Centered where a minimum size makes the surface larger than its content
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        ) {
            Icon(
                if (follow) Icons.TwoTone.CenterFocusStrong else Icons.TwoTone.CenterFocusWeak,
                contentDescription = if (compact) label else null,
                modifier = Modifier.size(20.dp),
            )
            if (!compact) {
                Text(
                    text = stringResource(R.string.map_control_follow),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun FloatingChip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 4.dp,
        modifier = modifier,
    ) {
        // Inside the surface, so growing into its label doesn't clip the shadow
        Row(
            modifier = Modifier
                .animateContentSize()
                .then(
                    if (compact) {
                        Modifier.padding(9.dp)
                    } else {
                        Modifier.padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                    }
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            content()
        }
    }
}

/** Shrinks and fades with [fade] together with the [gap] towards its next neighbour. */
private fun Modifier.fadingSlot(fade: () -> Float, gap: Dp = 8.dp): Modifier = this
    .layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val shown = 1f - fade()
        val slot = ((placeable.width + gap.roundToPx()) * shown).roundToInt()
        // Shrinks around its own center, which moves along with the shrinking slot
        val center = placeable.width * shown / 2f
        layout(slot, placeable.height) {
            placeable.placeRelative((center - placeable.width / 2f).roundToInt(), 0)
        }
    }
    .fading(fade)

private fun Modifier.fading(fade: () -> Float): Modifier = graphicsLayer {
    val shown = 1f - fade()
    alpha = shown
    scaleX = shown
    scaleY = shown
}

@Composable
private fun SquawkBanner(
    squawk: String,
    emergency: SquawkEmergency,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Text(
            text = stringResource(R.string.map_aircraft_details_squawk_banner, squawk, stringResource(emergency.meaningRes)),
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun Airport.fullLabel(): String {
    val label = name?.let { stringResource(R.string.airport_name_with_code_x, it, displayLabel) } ?: displayLabel
    return listOfNotNull(label, countryName).joinToString(" \u00b7 ")
}

@Composable
private fun Airport.detailLink(onClick: ((Airport) -> Unit)?): DetailLink? =
    onClick?.let { DetailLink(clickLabel()) { it(this) } }

/** Compared by content, a plain list would count as changed whenever it is built again. */
@Immutable
private data class DetailPairs(val items: List<DetailPair>)

private data class DetailPair(
    val label: String,
    val value: String,
    val wide: Boolean = false,
    val mono: Boolean = false,
    val alert: Boolean = false,
    val link: DetailLink? = null,
    /** Description of a copy button after the value, which has none without it. */
    val copyLabel: String? = null,
)

private data class DetailLink(val clickLabel: String, val onClick: () -> Unit)

@Composable
private fun DetailSection(
    header: String,
    pairs: DetailPairs,
    onCopy: ((String) -> Unit)?,
) {
    if (pairs.items.isEmpty()) return

    Text(
        text = header,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )

    // Two pairs share a row, a wide one takes a row of its own, so missing values leave no holes
    val rows = buildList {
        var pending: DetailPair? = null
        pairs.items.forEach { pair ->
            when {
                pair.wide -> {
                    pending?.let { add(listOf(it)) }
                    pending = null
                    add(listOf(pair))
                }

                pending == null -> pending = pair
                else -> {
                    add(listOf(pending!!, pair))
                    pending = null
                }
            }
        }
        pending?.let { add(listOf(it)) }
    }

    rows.forEach { row ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            row.forEach { pair ->
                DetailCell(pair = pair, onCopy = onCopy, modifier = Modifier.weight(if (pair.wide) 2f else 1f))
            }
            if (row.size == 1 && !row.first().wide) Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun DetailCell(pair: DetailPair, onCopy: ((String) -> Unit)?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = pair.label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(if (pair.wide) 0.22f else 0.45f)
                .padding(end = 8.dp),
        )
        val valueModifier = Modifier.weight(if (pair.wide) 0.78f else 0.55f)
        val valueStyle = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = if (pair.mono) FontFamily.Monospace else null,
        )
        val valueActions = detailValueActions(pair, onCopy)
        val copyLabel = pair.copyLabel
        when {
            pair.alert -> Surface(
                modifier = valueModifier.then(valueActions),
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ) {
                Text(
                    text = pair.value,
                    style = valueStyle,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            onCopy != null && copyLabel != null -> Row(
                modifier = valueModifier,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DetailValue(pair = pair, style = valueStyle, modifier = Modifier.weight(1f, fill = false).then(valueActions))
                CopyButton(description = copyLabel, onClick = { onCopy(pair.value) })
            }

            else -> DetailValue(
                pair = pair,
                style = valueStyle,
                modifier = if (pair.link != null) {
                    // Only the value itself is the link, not the free space next to it
                    valueModifier.wrapContentWidth(Alignment.Start).then(valueActions)
                } else {
                    valueModifier.then(valueActions)
                },
            )
        }
    }
}

@Composable
private fun DetailValue(pair: DetailPair, style: TextStyle, modifier: Modifier = Modifier) {
    val link = pair.link != null
    Text(
        text = pair.value,
        style = style,
        color = if (link) MaterialTheme.colorScheme.primary else Color.Unspecified,
        textDecoration = if (link) TextDecoration.Underline else null,
        maxLines = if (pair.wide) 2 else 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** A link's tap, and a long press that copies the value. */
@Composable
private fun detailValueActions(pair: DetailPair, onCopy: ((String) -> Unit)?): Modifier {
    val haptics = LocalHapticFeedback.current
    val copyLabel = stringResource(R.string.common_copy_action)
    val copy: (() -> Unit)? = onCopy?.let {
        {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            it(pair.value)
        }
    }
    val currentCopy by rememberUpdatedState(copy)
    val link = pair.link
    return when {
        link != null -> Modifier.combinedClickable(
            onClickLabel = link.clickLabel,
            role = Role.Button,
            onLongClickLabel = copyLabel.takeIf { copy != null },
            onLongClick = copy,
            hapticFeedbackEnabled = false,
            onClick = link.onClick,
        )

        // Not combinedClickable, a value that isn't a link must not offer a click
        copy != null -> Modifier
            .pointerInput(Unit) { detectTapGestures(onLongPress = { currentCopy?.invoke() }) }
            .semantics {
                onLongClick(label = copyLabel) {
                    currentCopy?.invoke()
                    true
                }
            }

        else -> Modifier
    }
}

/**
 * Smaller than the value's line, so the row keeps its height. Compose still sends it touches from within the minimum
 * touch target around it (Compose UI 1.12, NodeCoordinator.hitTest), unless they land on another touch target.
 */
@Composable
private fun CopyButton(description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(start = 4.dp)
            .size(16.dp)
            .clickable(
                interactionSource = null,
                indication = ripple(bounded = false, radius = 16.dp),
                role = Role.Button,
                onClick = onClick,
            ),
    ) {
        Icon(
            Icons.TwoTone.ContentCopy,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private fun previewDetails(squawk: String = "1234") = MapAircraftDetails(
    hex = "4CA2B1", callsign = "RYR1234", registration = "EI-ABC",
    country = "Ireland", icaoType = "B738", typeLong = "Boeing 737-800", operator = "Ryanair",
    altitudeFt = 35_000, altitudeGeomFt = 35_350, vertRateFpm = 1_200,
    groundSpeedKt = 450f, iasKt = 280, trackDegrees = 270f, headingDegrees = 268.0,
    squawk = squawk, tempC = -54, position = "51.5000, -0.1000", sourceType = AircraftSourceType.ADSB,
)

@Preview2
@Composable
private fun AircraftGlanceCollapsedPreview() {
    PreviewWrapper {
        AircraftGlance(
            details = previewDetails(), loading = false, note = null, route = null, progress = { 0f },
            onCollapsedHeight = {}, onPhotoClick = {}, seen = NativeMapViewModel.Seen.Live,
        )
    }
}

@Preview2
@Composable
private fun AircraftGlanceNotLivePreview() {
    PreviewWrapper {
        AircraftGlance(
            details = previewDetails(), loading = false, note = null, route = null, progress = { 0f },
            onCollapsedHeight = {}, onPhotoClick = {},
            seen = NativeMapViewModel.Seen.At(millis = 1_700_000_000_000L), serverNow = { 1_700_000_420_000L },
        )
    }
}

@Preview2
@Composable
private fun AircraftGlanceSeenUnknownPreview() {
    PreviewWrapper {
        AircraftGlance(
            details = previewDetails(), loading = false, note = null, route = null, progress = { 0f },
            onCollapsedHeight = {}, onPhotoClick = {}, seen = NativeMapViewModel.Seen.Unknown,
        )
    }
}

@Preview2
@Composable
private fun AircraftGlanceExpandedPreview() {
    PreviewWrapper {
        AircraftGlance(
            details = previewDetails(), loading = false, note = null, route = null, progress = { 1f },
            onCollapsedHeight = {}, onPhotoClick = {},
        )
    }
}

@Preview2
@Composable
private fun AircraftGlanceLoadingPreview() {
    PreviewWrapper {
        AircraftGlance(
            details = MapAircraftDetails(hex = "4CA2B1", isPartial = true, callsign = "RYR1234", icaoType = "B738"),
            loading = true, note = null, route = null, progress = { 0f },
            onCollapsedHeight = {}, onPhotoClick = {},
        )
    }
}

@Preview2
@Composable
private fun AircraftGlanceEmergencyPreview() {
    PreviewWrapper {
        AircraftGlance(
            details = previewDetails(squawk = "7700"), loading = false, note = null, route = null, progress = { 0f },
            onCollapsedHeight = {}, onPhotoClick = {},
        )
    }
}
