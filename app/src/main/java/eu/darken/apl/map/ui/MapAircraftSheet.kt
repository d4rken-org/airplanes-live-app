package eu.darken.apl.map.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material.icons.twotone.Flight
import androidx.compose.material.icons.twotone.GpsFixed
import androidx.compose.material.icons.twotone.GpsNotFixed
import androidx.compose.material.icons.twotone.Navigation
import androidx.compose.material.icons.twotone.Search
import androidx.compose.material.icons.twotone.Share
import androidx.compose.material.icons.twotone.Visibility
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.util.lerp
import eu.darken.apl.R
import eu.darken.apl.common.compose.Preview2
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.common.flight.Airport
import eu.darken.apl.common.flight.FlightRoute
import eu.darken.apl.common.planespotters.PlanespottersThumbnail
import eu.darken.apl.common.planespotters.coil.AircraftThumbnailQuery
import eu.darken.apl.main.core.aircraft.SquawkEmergency
import eu.darken.apl.map.core.MapAircraftDetails
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
    progress: () -> Float,
    topInset: Int,
    maxPhotoHeight: Int,
    onCollapsedHeight: (Int) -> Unit,
    onShowInSearch: (String) -> Unit,
    onAddWatch: (String) -> Unit,
    onPhotoClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    LaunchedEffect(details.hex) { listState.scrollToItem(0) }

    val currentProgress by rememberUpdatedState(progress)
    // The drag that opens the sheet ends there, its rest would scroll the photo out of sight
    val openingDrag = remember {
        object : NestedScrollConnection {
            var opening: Boolean? = null

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val isOpening = opening ?: (currentProgress() < 1f).also { opening = it }
                return if (isOpening && available.y < 0) available else Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val wasOpening = opening == true
                opening = null
                return if (wasOpening && available.y < 0) available else Velocity.Zero
            }
        }
    }

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
                progress = progress,
                maxPhotoHeight = maxPhotoHeight,
                onCollapsedHeight = onCollapsedHeight,
                onPhotoClick = onPhotoClick,
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
                    SheetAction(
                        icon = Icons.TwoTone.Search,
                        label = stringResource(R.string.common_show_in_search),
                        onClick = { onShowInSearch(details.hex) },
                        modifier = Modifier.weight(1f),
                    )
                    SheetAction(
                        icon = Icons.TwoTone.Visibility,
                        label = stringResource(R.string.watch_list_watch_add_label),
                        onClick = { onAddWatch(details.hex) },
                        modifier = Modifier.weight(1f),
                    )
                }

                DetailSection(
                    header = stringResource(R.string.map_aircraft_details_section_route),
                    pairs = listOfNotNull(
                        route?.origin?.let {
                            DetailPair(stringResource(R.string.map_aircraft_details_route_from), it.fullLabel, wide = true)
                        },
                        route?.destination?.let {
                            DetailPair(stringResource(R.string.map_aircraft_details_route_to), it.fullLabel, wide = true)
                        },
                    ),
                )

                DetailSection(
                    header = stringResource(R.string.map_aircraft_details_section_aircraft),
                    pairs = listOfNotNull(
                        details.registration?.let { DetailPair(stringResource(R.string.common_registration_label), it) },
                        DetailPair(stringResource(R.string.common_hex_label), details.hex, mono = true),
                        details.icaoType?.let { DetailPair(stringResource(R.string.common_airframe_label), it) },
                        details.operator?.let { DetailPair(stringResource(R.string.map_aircraft_details_operator_label), it) },
                        details.country?.let { DetailPair(stringResource(R.string.map_aircraft_details_country_label), it) },
                        details.dbFlags?.let { DetailPair(stringResource(R.string.map_aircraft_details_flags_label), it) },
                    ),
                )

                DetailSection(
                    header = stringResource(R.string.map_aircraft_details_section_flight_data),
                    pairs = listOfNotNull(
                        details.altitude?.let { DetailPair(stringResource(R.string.common_altitude_label), it) },
                        details.altitudeGeom?.let { DetailPair(stringResource(R.string.map_aircraft_details_altitude_gps_label), it) },
                        details.vertRate?.let { DetailPair(stringResource(R.string.map_aircraft_details_vert_rate_label), it) },
                        details.squawk?.let {
                            DetailPair(
                                stringResource(R.string.common_squawk_label),
                                it,
                                mono = true,
                                alert = SquawkEmergency.fromSquawk(it) != null,
                            )
                        },
                        details.speed?.let { DetailPair(stringResource(R.string.common_speed_label), it) },
                        details.ias?.let { DetailPair(stringResource(R.string.map_aircraft_details_ias_label), it) },
                        details.track?.let { DetailPair(stringResource(R.string.map_aircraft_details_track_label), it) },
                        details.heading?.let { DetailPair(stringResource(R.string.map_aircraft_details_heading_label), it) },
                        details.temp?.let { DetailPair(stringResource(R.string.map_aircraft_details_temp_label), it) },
                    ),
                )

                DetailSection(
                    header = stringResource(R.string.map_aircraft_details_section_position),
                    pairs = listOfNotNull(
                        details.position?.let {
                            DetailPair(stringResource(R.string.map_aircraft_details_position_label), it, wide = true)
                        },
                        details.source?.let { DetailPair(stringResource(R.string.map_aircraft_details_source_label), it) },
                    ),
                )
            }
        }
    }
}

// The text first drops below the small photo, then the photo widens, so the two never overlap
private const val TEXT_STAGE_END = 0.35f
private const val PHOTO_ASPECT = 3f / 2f

@Composable
private fun AircraftGlance(
    details: MapAircraftDetails,
    loading: Boolean,
    note: String?,
    route: FlightRoute?,
    progress: () -> Float,
    onCollapsedHeight: (Int) -> Unit,
    onPhotoClick: (String) -> Unit,
    maxPhotoHeight: Int = 0,
) {
    val currentOnCollapsedHeight by rememberUpdatedState(onCollapsedHeight)
    val headline = details.callsign ?: details.registration ?: details.hex
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

    Layout(
        contents = listOf(
            {
                PlanespottersThumbnail(
                    query = photoQuery,
                    shape = RectangleShape,
                    aspectRatio = null,
                    onImageClick = { meta -> onPhotoClick(meta.link) },
                    onNoPhoto = { if (photoQuery.registration == null) hexFoundNoPhoto = true },
                    modifier = Modifier.graphicsLayer {
                        clip = true
                        shape = RoundedCornerShape(bottomEnd = 16.dp * (1f - progress().coerceIn(0f, 1f)))
                    },
                )
            },
            { GlanceIdentity(details = details, headline = headline, loading = loading, note = note) },
            {
                details.operator?.let { GlanceOwnerLine(it) }
                listOfNotNull(details.registration.takeIf { it != headline }, details.country)
                    .takeIf { it.isNotEmpty() }
                    ?.let { GlanceOwnerLine(it.joinToString(" \u00b7 ")) }
            },
            { GlanceStatus(details = details, route = route) },
        ),
    ) { (photoMeasurables, identityMeasurables, ownerMeasurables, statusMeasurables), constraints ->
        val p = progress().coerceIn(0f, 1f)
        val textStage = (p / TEXT_STAGE_END).coerceIn(0f, 1f)
        val photoStage = ((p - TEXT_STAGE_END) / (1f - TEXT_STAGE_END)).coerceIn(0f, 1f)

        val width = constraints.maxWidth
        val padH = 16.dp.roundToPx()
        val padTop = 12.dp.roundToPx()
        val padBottom = 12.dp.roundToPx()
        val gap = 12.dp.roundToPx()
        val smallWidth = 144.dp.roundToPx().coerceAtMost((width * 0.4f).roundToInt())
        val smallHeight = (smallWidth / PHOTO_ASPECT).roundToInt()

        // On short screens the grown photo is capped so the text below it stays visible
        val fullWidth = when {
            maxPhotoHeight > 0 -> min(width, (maxPhotoHeight * PHOTO_ASPECT).roundToInt()).coerceAtLeast(smallWidth)
            else -> width
        }
        val photoWidth = lerp(smallWidth, fullWidth, photoStage)
        val photoHeight = (photoWidth / PHOTO_ASPECT).roundToInt()
        val photoX = lerp(0, (width - fullWidth) / 2, photoStage)
        val photo = photoMeasurables.first().measure(Constraints.fixed(photoWidth, photoHeight))

        // Collapsed the text stacks beside the photo, expanded the owner lines move to the end of the identity's row
        val besideX = smallWidth + gap
        val besideWidth = (width - besideX - padH).coerceAtLeast(0)
        val belowWidth = (width - 2 * padH).coerceAtLeast(0)
        val identity = identityMeasurables.first().measure(
            Constraints(maxWidth = lerp(besideWidth, (belowWidth * 0.6f).roundToInt(), photoStage))
        )
        val textX = lerp(besideX, padH, photoStage)
        // The owner lines first move to the end below the identity, then rise into its row
        val ownerXStage = (photoStage * 2f).coerceIn(0f, 1f)
        val ownerYStage = (photoStage * 2f - 1f).coerceIn(0f, 1f)
        val ownersBelowWidth = (width - padH - (textX + identity.width) - gap).coerceAtLeast(0)
        val owners = ownerMeasurables.map {
            it.measure(Constraints(maxWidth = lerp(besideWidth, ownersBelowWidth, ownerXStage).coerceAtLeast(0)))
        }
        val ownersHeight = owners.sumOf { it.height }

        val textY = lerp(padTop, photoHeight + gap, textStage)
        val ownersY = lerp(identity.height, 0, ownerYStage)
        val textBottom = textY + max(identity.height, ownersY + ownersHeight)

        val status = statusMeasurables.first().measure(Constraints.fixedWidth(belowWidth))

        // Every text line is single-line, so their heights don't depend on the widths they got
        val collapsedHeader = max(smallHeight, padTop + identity.height + ownersHeight)
        currentOnCollapsedHeight(collapsedHeader + gap + status.height + padBottom)

        val header = max(photoHeight, textBottom)
        val height = header + gap + status.height + padBottom

        layout(width, height) {
            photo.placeRelative(photoX, 0)
            identity.placeRelative(textX, textY)
            var lineY = textY + ownersY
            owners.forEach { line ->
                val besideLineX = textX
                val endLineX = width - padH - line.width
                line.placeRelative(lerp(besideLineX, endLineX, ownerXStage), lineY)
                lineY += line.height
            }
            status.placeRelative(padH, header + gap)
        }
    }
}

@Composable
private fun GlanceIdentity(details: MapAircraftDetails, headline: String, loading: Boolean, note: String?) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = headline,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                )
            }
        }
        val type = details.typeLong ?: details.icaoType
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
private fun GlanceOwnerLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun GlanceStatus(
    details: MapAircraftDetails,
    route: FlightRoute?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val emergency = SquawkEmergency.fromSquawk(details.squawk)
        if (emergency != null) SquawkBanner(squawk = details.squawk!!, emergency = emergency)
        if (route != null && (route.origin != null || route.destination != null)) GlanceRoute(route = route)
        Row(modifier = Modifier.fillMaxWidth()) {
            GlanceMetric(
                value = details.altitude,
                label = stringResource(R.string.common_altitude_label),
                modifier = Modifier.weight(1f),
            )
            GlanceMetric(
                value = details.vertRateShort,
                label = stringResource(R.string.map_aircraft_details_vs_label),
                modifier = Modifier.weight(1f),
            )
            GlanceMetric(
                value = details.speed,
                label = stringResource(R.string.common_speed_label),
                modifier = Modifier.weight(1f),
            )
            GlanceMetric(
                value = details.track,
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

@Composable
private fun GlanceRoute(route: FlightRoute) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = route.origin?.displayLabel ?: "?",
                style = MaterialTheme.typography.titleMedium,
            )
            HorizontalDivider(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            )
            Icon(
                Icons.TwoTone.Flight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(16.dp)
                    .rotate(90f),
            )
            HorizontalDivider(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            )
            Text(
                text = route.destination?.displayLabel ?: "?",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        val originName = route.origin?.let { it.name ?: it.countryName }
        val destinationName = route.destination?.let { it.name ?: it.countryName }
        if (originName != null || destinationName != null) {
            Row {
                Text(
                    text = originName ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = destinationName ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun GlanceMetric(
    value: String?,
    label: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = value ?: "---",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (value != null) trailing()
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Share and close float above the sheet's top end. [sheetTop] is the sheet's offset in this layout's
 * coordinates; once the sheet rises too far they stay at [safeTop], over the grown photo, and scroll
 * away with it by [contentScroll].
 */
@Composable
internal fun SheetFloatingActions(
    sheetTop: () -> Float?,
    contentScroll: () -> Int,
    progress: () -> Float,
    safeTop: Int,
    sheetMaxWidth: Int,
    endInset: Int,
    onRowHeight: (Int) -> Unit,
    follow: Boolean,
    onToggleFollow: () -> Unit,
    onShare: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentProgress by rememberUpdatedState(progress)
    val currentOnRowHeight by rememberUpdatedState(onRowHeight)
    // Follow and close fade out while the sheet expands, an expanded sheet leaves no map to follow on
    val closeFade = { ((currentProgress() - 0.6f) / 0.35f).coerceIn(0f, 1f) }
    val closeHidden by remember { derivedStateOf { closeFade() >= 1f } }
    Layout(
        content = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!closeHidden) {
                    Surface(
                        checked = follow,
                        onCheckedChange = { onToggleFollow() },
                        shape = CircleShape,
                        color = if (follow) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .fadingSlot(closeFade, leading = true)
                            .size(36.dp),
                    ) {
                        Icon(
                            if (follow) Icons.TwoTone.GpsFixed else Icons.TwoTone.GpsNotFixed,
                            contentDescription = stringResource(if (follow) R.string.map_control_unfollow else R.string.map_control_follow),
                            tint = if (follow) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
                Surface(
                    onClick = onShare,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = 4.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.TwoTone.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(
                            text = stringResource(R.string.common_share_action),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
                if (!closeHidden) {
                    Surface(
                        onClick = onClose,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .fadingSlot(closeFade, leading = false)
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
        },
        modifier = modifier
            .fillMaxSize()
            .clipToBounds(),
    ) { measurables, constraints ->
        val row = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
        currentOnRowHeight(row.height)
        layout(constraints.maxWidth, constraints.maxHeight) {
            val top = sheetTop() ?: return@layout
            val gap = 8.dp.roundToPx()
            val sheetWidth = min(constraints.maxWidth, sheetMaxWidth)
            val sheetEnd = (constraints.maxWidth + sheetWidth) / 2
            val y = max(top.roundToInt() - row.height - gap, safeTop + gap) - contentScroll()
            val margin = 12.dp.roundToPx()
            val x = min(sheetEnd - margin - row.width, constraints.maxWidth - endInset - margin - row.width)
            row.placeRelative(x, y)
        }
    }
}

/** Shrinks and fades with [fade] together with the 8dp gap towards its neighbour. */
private fun Modifier.fadingSlot(fade: () -> Float, leading: Boolean): Modifier = this
    .layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val shown = 1f - fade()
        val gap = 8.dp.roundToPx()
        val slot = ((placeable.width + gap) * shown).roundToInt()
        // Shrinks around its own center, which moves along with the shrinking slot
        val center = (if (leading) 0f else gap * shown) + placeable.width * shown / 2f
        layout(slot, placeable.height) {
            placeable.placeRelative((center - placeable.width / 2f).roundToInt(), 0)
        }
    }
    .graphicsLayer {
        val shown = 1f - fade()
        alpha = shown
        scaleX = shown
        scaleY = shown
    }

@Composable
private fun SheetAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
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

private val Airport.fullLabel: String
    get() = listOfNotNull(routeDisplayText, countryName).joinToString(" \u00b7 ")

private data class DetailPair(
    val label: String,
    val value: String,
    val wide: Boolean = false,
    val mono: Boolean = false,
    val alert: Boolean = false,
)

@Composable
private fun DetailSection(
    header: String,
    pairs: List<DetailPair>,
) {
    if (pairs.isEmpty()) return

    Text(
        text = header,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )

    // Two pairs share a row, a wide one takes a row of its own, so missing values leave no holes
    val rows = buildList {
        var pending: DetailPair? = null
        pairs.forEach { pair ->
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
                DetailCell(pair = pair, modifier = Modifier.weight(if (pair.wide) 2f else 1f))
            }
            if (row.size == 1 && !row.first().wide) Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun DetailCell(pair: DetailPair, modifier: Modifier = Modifier) {
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
        if (pair.alert) {
            Surface(
                modifier = valueModifier,
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
        } else {
            Text(
                text = pair.value,
                style = valueStyle,
                maxLines = if (pair.wide) 2 else 1,
                overflow = TextOverflow.Ellipsis,
                modifier = valueModifier,
            )
        }
    }
}

private fun previewDetails(squawk: String = "1234") = MapAircraftDetails(
    hex = "4CA2B1", callsign = "RYR1234", registration = "EI-ABC",
    country = "Ireland", icaoType = "B738", typeLong = "Boeing 737-800", operator = "Ryanair",
    altitude = "35,000 ft", altitudeGeom = "35,350 ft",
    vertRate = "+1,200 ft/min", vertRateShort = "+1.2k fpm",
    speed = "450 kts", ias = "280 kts", trackDegrees = 270f, track = "270°", heading = "268°",
    squawk = squawk, temp = "-54 °C", position = "51.5000, -0.1000", source = "ADS-B",
)

@Preview2
@Composable
private fun AircraftGlanceCollapsedPreview() {
    PreviewWrapper {
        AircraftGlance(
            details = previewDetails(), loading = false, note = null, route = null, progress = { 0f },
            onCollapsedHeight = {}, onPhotoClick = {},
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
