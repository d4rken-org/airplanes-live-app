package eu.darken.apl.map.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.TrendingDown
import androidx.compose.material.icons.automirrored.twotone.TrendingFlat
import androidx.compose.material.icons.automirrored.twotone.TrendingUp
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material.icons.twotone.Navigation
import androidx.compose.material.icons.twotone.Search
import androidx.compose.material.icons.twotone.Share
import androidx.compose.material.icons.twotone.Visibility
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.util.lerp
import eu.darken.apl.R
import eu.darken.apl.common.compose.InfoCell
import eu.darken.apl.common.compose.Preview2
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.common.flight.Airport
import eu.darken.apl.common.flight.FlightRoute
import eu.darken.apl.common.flight.ui.HorizontalRouteBar
import eu.darken.apl.common.planespotters.PlanespottersThumbnail
import eu.darken.apl.common.planespotters.coil.AircraftThumbnailQuery
import eu.darken.apl.map.core.MapAircraftDetails
import kotlin.math.min
import kotlin.math.roundToInt

private enum class SquawkEmergency(val code: String, @StringRes val meaningRes: Int) {
    HIJACK("7500", R.string.map_aircraft_details_squawk_7500),
    RADIO_FAILURE("7600", R.string.map_aircraft_details_squawk_7600),
    EMERGENCY("7700", R.string.map_aircraft_details_squawk_7700);

    companion object {
        fun fromSquawk(squawk: String?): SquawkEmergency? =
            squawk?.trim()?.let { trimmed -> entries.find { it.code == trimmed } }
    }
}

/**
 * [progress] runs from 0 (collapsed) to 1 (expanded) and is only read during layout and drawing.
 * [onCollapsedHeight] reports the height of the glance item at progress 0.
 */
@Composable
internal fun AircraftDetailsSheetContent(
    details: MapAircraftDetails,
    route: FlightRoute?,
    progress: () -> Float,
    topInset: Int,
    maxPhotoHeight: Int,
    onCollapsedHeight: (Int) -> Unit,
    onClose: () -> Unit,
    onShare: () -> Unit,
    onShowInSearch: (String) -> Unit,
    onAddWatch: (String) -> Unit,
    onPhotoClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(details.hex) { listState.scrollToItem(0) }

    LazyColumn(
        state = listState,
        // Full height keeps the expanded sheet at the top, which progress is measured against
        modifier = modifier
            .fillMaxSize()
            .layout { measurable, constraints ->
                val inset = (topInset * progress().coerceIn(0f, 1f)).roundToInt()
                val placeable = measurable.measure(constraints.offset(vertical = -inset))
                layout(placeable.width, placeable.height + inset) { placeable.place(0, inset) }
            },
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item(key = "glance") {
            AircraftGlance(
                details = details,
                route = route,
                progress = progress,
                maxPhotoHeight = maxPhotoHeight,
                onCollapsedHeight = onCollapsedHeight,
                onShare = onShare,
                onClose = onClose,
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
                        icon = Icons.TwoTone.Share,
                        label = stringResource(R.string.common_share_action),
                        onClick = onShare,
                        modifier = Modifier.weight(1f),
                    )
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

                if (route != null && (route.origin?.name != null || route.destination?.name != null)) {
                    RouteDetailCard(route = route)
                }

                DetailSection(
                    header = stringResource(R.string.map_aircraft_details_section_aircraft),
                    rows = listOf(
                        DetailRow(
                            stringResource(R.string.common_registration_label) to details.registration,
                            stringResource(R.string.common_hex_label) to details.hex,
                            rightMono = true,
                        ),
                        DetailRow(
                            stringResource(R.string.common_airframe_label) to details.icaoType,
                            stringResource(R.string.map_aircraft_details_operator_label) to details.operator,
                        ),
                        DetailRow(
                            stringResource(R.string.map_aircraft_details_country_label) to details.country,
                            stringResource(R.string.map_aircraft_details_flags_label) to details.dbFlags,
                        ),
                    ),
                )

                DetailSection(
                    header = stringResource(R.string.map_aircraft_details_section_flight_data),
                    rows = listOf(
                        DetailRow(
                            stringResource(R.string.common_altitude_label) to details.altitude,
                            stringResource(R.string.map_aircraft_details_altitude_gps_label) to details.altitudeGeom,
                        ),
                        DetailRow(
                            stringResource(R.string.map_aircraft_details_vert_rate_label) to details.vertRate,
                            stringResource(R.string.common_squawk_label) to details.squawk,
                            rightIsAlert = SquawkEmergency.fromSquawk(details.squawk) != null,
                            rightMono = true,
                        ),
                        DetailRow(
                            stringResource(R.string.common_speed_label) to details.speed,
                            stringResource(R.string.map_aircraft_details_ias_label) to details.ias,
                        ),
                        DetailRow(
                            stringResource(R.string.map_aircraft_details_track_label) to details.track,
                            stringResource(R.string.map_aircraft_details_heading_label) to details.heading,
                        ),
                        DetailRow(
                            stringResource(R.string.map_aircraft_details_temp_label) to details.temp,
                            null,
                        ),
                    ),
                )

                DetailSection(
                    header = stringResource(R.string.map_aircraft_details_section_position),
                    rows = listOf(
                        DetailRow(
                            stringResource(R.string.map_aircraft_details_position_label) to details.position,
                            stringResource(R.string.map_aircraft_details_source_label) to details.source,
                        ),
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
    route: FlightRoute?,
    progress: () -> Float,
    onCollapsedHeight: (Int) -> Unit,
    onShare: () -> Unit,
    onClose: () -> Unit,
    onPhotoClick: (String) -> Unit,
    maxPhotoHeight: Int = 0,
) {
    val currentOnCollapsedHeight by rememberUpdatedState(onCollapsedHeight)

    Layout(
        contents = listOf(
            {
                PlanespottersThumbnail(
                    query = AircraftThumbnailQuery(
                        hex = details.hex,
                        registration = details.registration,
                        large = true,
                    ),
                    shape = RoundedCornerShape(8.dp),
                    aspectRatio = null,
                    onImageClick = { meta -> onPhotoClick(meta.link) },
                )
            },
            { GlanceTitle(details = details) },
            { GlanceActions(progress = progress, onShare = onShare, onClose = onClose) },
            { GlanceStatus(details = details, route = route) },
        ),
    ) { (photoMeasurables, titleMeasurables, actionsMeasurables, statusMeasurables), constraints ->
        val p = progress().coerceIn(0f, 1f)
        val textStage = (p / TEXT_STAGE_END).coerceIn(0f, 1f)
        val photoStage = ((p - TEXT_STAGE_END) / (1f - TEXT_STAGE_END)).coerceIn(0f, 1f)

        val padH = 16.dp.roundToPx()
        val padTop = 16.dp.roundToPx()
        val padBottom = 12.dp.roundToPx()
        val gap = 12.dp.roundToPx()
        val inner = (constraints.maxWidth - 2 * padH).coerceAtLeast(0)
        val smallWidth = 96.dp.roundToPx().coerceAtMost(inner / 3)
        val smallHeight = (smallWidth / PHOTO_ASPECT).roundToInt()

        // On short screens the grown photo is capped so the text below it stays visible
        val fullWidth = when {
            maxPhotoHeight > 0 -> min(inner, (maxPhotoHeight * PHOTO_ASPECT).roundToInt()).coerceAtLeast(smallWidth)
            else -> inner
        }
        val photoWidth = lerp(smallWidth, fullWidth, photoStage)
        val photoHeight = (photoWidth / PHOTO_ASPECT).roundToInt()
        val photoX = lerp(0, (inner - fullWidth) / 2, photoStage)
        val photo = photoMeasurables.first().measure(Constraints.fixed(photoWidth, photoHeight))

        val actions = actionsMeasurables.first().measure(Constraints())
        val textX = lerp(smallWidth + gap, 0, photoStage)
        val textY = lerp(0, photoHeight + gap, textStage)
        // The title only gets the actions' width once it has moved below them
        val textRight = if (textY < actions.height) actions.width else 0
        val title = titleMeasurables.first().measure(
            Constraints.fixedWidth((inner - textX - textRight).coerceAtLeast(0))
        )

        val status = statusMeasurables.first().measure(Constraints.fixedWidth(inner))

        // Every title line is single-line, so its height doesn't depend on the width it got
        val collapsedHeader = maxOf(smallHeight, title.height, actions.height)
        currentOnCollapsedHeight(padTop + collapsedHeader + gap + status.height + padBottom)

        val header = maxOf(photoHeight, textY + title.height, actions.height)
        val height = padTop + header + gap + status.height + padBottom

        layout(constraints.maxWidth, height) {
            photo.placeRelative(padH + photoX, padTop)
            title.placeRelative(padH + textX, padTop + textY)
            actions.placeRelative(padH + inner - actions.width, padTop)
            status.placeRelative(padH, padTop + header + gap)
        }
    }
}

@Composable
private fun GlanceTitle(details: MapAircraftDetails) {
    val headline = details.callsign ?: details.registration ?: details.hex
    Column {
        Text(
            text = headline,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val type = details.typeLong ?: details.icaoType
        if (type != null) {
            Text(
                text = type,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val owner = listOfNotNull(
            details.operator,
            details.registration.takeIf { it != headline },
        ).joinToString(" \u00b7 ")
        if (owner.isNotEmpty()) {
            Text(
                text = owner,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun GlanceActions(
    progress: () -> Float,
    onShare: () -> Unit,
    onClose: () -> Unit,
) {
    // Over the grown photo the icons need a backing to stay readable
    val backing = MaterialTheme.colorScheme.surface
    val backingModifier = Modifier.drawBehind {
        drawCircle(color = backing.copy(alpha = 0.8f * progress().coerceIn(0f, 1f)))
    }
    Row {
        IconButton(onClick = onShare, modifier = Modifier.size(40.dp)) {
            Box(modifier = Modifier.size(36.dp).then(backingModifier), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.TwoTone.Share,
                    contentDescription = stringResource(R.string.common_share_action),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
            Box(modifier = Modifier.size(36.dp).then(backingModifier), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.TwoTone.Close,
                    contentDescription = stringResource(R.string.common_close_action),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun GlanceStatus(
    details: MapAircraftDetails,
    route: FlightRoute?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val emergency = SquawkEmergency.fromSquawk(details.squawk)
        if (emergency != null) SquawkBanner(squawk = details.squawk!!, emergency = emergency)
        if (route != null && (route.origin != null || route.destination != null)) HorizontalRouteBar(route = route)
        Row(modifier = Modifier.fillMaxWidth()) {
            GlanceMetric(
                value = details.altitude,
                label = stringResource(R.string.common_altitude_label),
                modifier = Modifier.weight(1f),
            ) {
                val (icon, description) = when (details.climb) {
                    MapAircraftDetails.Climb.CLIMBING ->
                        Icons.AutoMirrored.TwoTone.TrendingUp to R.string.map_aircraft_details_climbing_label

                    MapAircraftDetails.Climb.DESCENDING ->
                        Icons.AutoMirrored.TwoTone.TrendingDown to R.string.map_aircraft_details_descending_label

                    MapAircraftDetails.Climb.LEVEL ->
                        Icons.AutoMirrored.TwoTone.TrendingFlat to R.string.map_aircraft_details_level_label

                    null -> return@GlanceMetric
                }
                Icon(icon, contentDescription = stringResource(description), modifier = Modifier.size(18.dp))
            }
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
                        .size(18.dp)
                        .rotate(degrees),
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
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = value ?: "---",
                style = MaterialTheme.typography.titleMedium,
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
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                text = label,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
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

@Composable
private fun RouteDetailCard(route: FlightRoute) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.map_aircraft_details_section_route),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp),
            )

            route.origin?.let { airport ->
                AirportRow(
                    label = "\u2191",
                    airport = airport,
                )
            }

            route.destination?.let { airport ->
                AirportRow(
                    label = "\u2193",
                    airport = airport,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun AirportRow(
    label: String,
    airport: Airport,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = airport.name ?: airport.displayLabel,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = listOfNotNull(
                airport.displayLabel.takeIf { airport.name != null },
                airport.countryName,
            ).joinToString(" \u00b7 ")
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private data class DetailRow(
    val left: Pair<String, String?>,
    val right: Pair<String, String?>?,
    val rightIsAlert: Boolean = false,
    val rightMono: Boolean = false,
)

@Composable
private fun DetailSection(
    header: String,
    rows: List<DetailRow>,
) {
    val visibleRows = rows.filter { row ->
        !row.left.second.isNullOrBlank() || row.right?.second?.isNotBlank() == true
    }
    if (visibleRows.isEmpty()) return

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = header,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp),
            )

            visibleRows.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                ) {
                    if (!row.left.second.isNullOrBlank()) {
                        InfoCell(
                            value = row.left.second!!,
                            label = row.left.first,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Box(modifier = Modifier.weight(1f))
                    }
                    if (row.right != null && !row.right.second.isNullOrBlank()) {
                        InfoCell(
                            value = row.right.second!!,
                            label = row.right.first,
                            modifier = Modifier.weight(1f),
                            isAlert = row.rightIsAlert,
                            monoValue = row.rightMono,
                        )
                    } else {
                        Box(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private fun previewDetails(squawk: String = "1234") = MapAircraftDetails(
    hex = "4CA2B1", callsign = "RYR1234", registration = "EI-ABC",
    country = "Ireland", icaoType = "B738", typeLong = "Boeing 737-800", operator = "Ryanair",
    altitude = "35,000 ft", altitudeGeom = "35,350 ft",
    climb = MapAircraftDetails.Climb.CLIMBING, vertRate = "+1,200 ft/min",
    speed = "450 kts", ias = "280 kts", trackDegrees = 270f, track = "270°", heading = "268°",
    squawk = squawk, temp = "-54 °C", position = "51.5000, -0.1000", source = "ADS-B", dbFlags = null,
)

@Preview2
@Composable
private fun AircraftGlanceCollapsedPreview() {
    PreviewWrapper {
        AircraftGlance(
            details = previewDetails(), route = null, progress = { 0f },
            onCollapsedHeight = {}, onShare = {}, onClose = {}, onPhotoClick = {},
        )
    }
}

@Preview2
@Composable
private fun AircraftGlanceExpandedPreview() {
    PreviewWrapper {
        AircraftGlance(
            details = previewDetails(), route = null, progress = { 1f },
            onCollapsedHeight = {}, onShare = {}, onClose = {}, onPhotoClick = {},
        )
    }
}

@Preview2
@Composable
private fun AircraftGlanceEmergencyPreview() {
    PreviewWrapper {
        AircraftGlance(
            details = previewDetails(squawk = "7700"), route = null, progress = { 0f },
            onCollapsedHeight = {}, onShare = {}, onClose = {}, onPhotoClick = {},
        )
    }
}
