package eu.darken.apl.airport.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.MenuBook
import androidx.compose.material.icons.twotone.CenterFocusStrong
import androidx.compose.material.icons.twotone.Language
import androidx.compose.material.icons.twotone.Map
import androidx.compose.material.icons.twotone.Radar
import androidx.compose.material.icons.twotone.Stars
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isFinite
import androidx.compose.ui.unit.offset
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.apl.R
import eu.darken.apl.airport.core.Airport
import eu.darken.apl.airport.core.AirportTarget
import eu.darken.apl.common.compose.Preview2
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.common.compose.SheetActionsAnchor
import eu.darken.apl.common.compose.SheetGlanceArrangement
import eu.darken.apl.common.compose.SheetGlanceDefaults
import eu.darken.apl.common.compose.SheetGlanceLayout
import eu.darken.apl.common.compose.grownMediaWidthDp
import eu.darken.apl.common.compose.rememberModalSheetProgress
import eu.darken.apl.common.compose.rememberSheetOpeningDrag
import eu.darken.apl.common.error.ErrorEventHandler
import eu.darken.apl.common.navigation.NavigationEventHandler
import eu.darken.apl.common.units.LocalUnitFormatter
import eu.darken.apl.common.units.text
import eu.darken.apl.watch.core.LocationWatchAvailability
import eu.darken.apl.watch.ui.thumbnail.MapPointThumbnailSpec
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun AirportDetailsSheetHost(
    target: AirportTarget,
    vm: AirportDetailsViewModel = hiltViewModel(),
) {
    NavigationEventHandler(vm)
    ErrorEventHandler(vm)

    LaunchedEffect(target) {
        vm.init(target)
    }

    val state by vm.state.collectAsStateWithLifecycle()
    val watchArea by vm.watchAreaAvailability.collectAsStateWithLifecycle()
    // Until init ran, what the target itself knows
    val initial = remember(target) { AirportDetailsState.of(target) }
    val current = state ?: initial

    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = { vm.navUp() }, sheetState = sheetState, dragHandle = null) {
        val progress = rememberModalSheetProgress(sheetState)
        BoxWithConstraints(modifier = progress.modifier) {
            val maxPhotoHeight = if (maxHeight.isFinite) with(LocalDensity.current) { (maxHeight * 0.45f).roundToPx() } else 0
            AirportDetailsSheetContent(
                display = current.display,
                lookingUp = current.lookingUp,
                watchArea = watchArea,
                placeAction = remember(vm) { AirportPlaceAction.ShowOnMap(vm::showOnMap) },
                onWatchArea = { vm.watchArea() },
                onOpenWebsite = { vm.openWebsite() },
                onOpenWikipedia = { vm.openWikipedia() },
                progress = progress,
                topInset = 0,
                maxPhotoHeight = maxPhotoHeight,
                onCollapsedHeight = {},
            )
            SheetActionsAnchor(sheetState = sheetState, label = current.display.title, onDismiss = { vm.navUp() })
        }
    }
}

/** The first action: Center on airport in the map's own sheet, Show on map everywhere else. */
internal sealed interface AirportPlaceAction {
    val onClick: () -> Unit

    data class CenterOnAirport(override val onClick: () -> Unit) : AirportPlaceAction
    data class ShowOnMap(override val onClick: () -> Unit) : AirportPlaceAction
}

/**
 * [progress] runs from 0 (collapsed) to 1 (expanded) and is only read during layout and drawing.
 * [onCollapsedHeight] reports the height of the glance item at progress 0.
 */
@Composable
internal fun AirportDetailsSheetContent(
    display: AirportDetailsDisplay,
    lookingUp: Boolean,
    watchArea: LocationWatchAvailability,
    placeAction: AirportPlaceAction,
    onWatchArea: () -> Unit,
    onOpenWebsite: () -> Unit,
    onOpenWikipedia: () -> Unit,
    progress: () -> Float,
    topInset: Int,
    maxPhotoHeight: Int,
    onCollapsedHeight: (Int) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
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
            AirportGlance(
                display = display,
                lookingUp = lookingUp,
                progress = progress,
                maxPhotoHeight = maxPhotoHeight,
                onCollapsedHeight = onCollapsedHeight,
            )
        }

        item(key = "details") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                AirportActions(
                    display = display,
                    watchArea = watchArea,
                    placeAction = placeAction,
                    onWatchArea = onWatchArea,
                    onOpenWebsite = onOpenWebsite,
                    onOpenWikipedia = onOpenWikipedia,
                )
                AirportFacts(display = display, modifier = Modifier.padding(top = 16.dp))
            }
        }
    }
}

/** Map snapshot at the sheet's top start, then name, the codes that identify it and where it is. */
@Composable
private fun AirportGlance(
    display: AirportDetailsDisplay,
    lookingUp: Boolean,
    progress: () -> Float,
    maxPhotoHeight: Int,
    onCollapsedHeight: (Int) -> Unit,
) {
    val currentProgress by rememberUpdatedState(progress)
    val fullyExpanded by remember { derivedStateOf { currentProgress() >= 1f } }
    val hasPosition = display.latitude != null && display.longitude != null
    val lookupDescription = stringResource(R.string.airport_details_lookup_progress)
    // Frankfurt: "FRA · EDDF", its other codes are in the facts below
    val headerCodes = display.codes
        .filter { it.kind.factLabel == null }
        .joinToString(" · ") { it.value }
    val place = listOfNotNull(display.municipality, display.country)
        .joinToString(stringResource(R.string.common_list_separator))

    BoxWithConstraints {
        // Rendered once at the size it grows to, so dragging the sheet never asks for a new map
        val spec = MapPointThumbnailSpec.header(LocalDensity.current.grownMediaWidthDp(constraints.maxWidth, maxPhotoHeight))
        SheetGlanceLayout(
            progress = progress,
            arrangement = SheetGlanceArrangement.STACKED,
            // The airport tile has nothing to show bigger
            growMedia = hasPosition,
            maxMediaHeight = maxPhotoHeight,
            mediaWidth = if (hasPosition) SheetGlanceDefaults.PhotoWidth else 88.dp,
            mediaAspect = if (hasPosition) SheetGlanceDefaults.PHOTO_ASPECT else 1f,
            onCollapsedHeight = onCollapsedHeight,
            media = {
                AirportMapThumbnail(
                    latitude = display.latitude,
                    longitude = display.longitude,
                    size = display.type?.size ?: Airport.Size.OTHER,
                    spec = spec,
                    modifier = Modifier.fillMaxSize(),
                )
            },
            identity = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = display.title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = if (fullyExpanded) 2 else 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (lookingUp) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .size(16.dp)
                                .semantics { contentDescription = lookupDescription },
                            strokeWidth = 2.dp,
                        )
                    }
                }
            },
            secondary = {
                if (headerCodes.isNotEmpty()) {
                    Text(
                        text = headerCodes,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (place.isNotEmpty()) {
                    Text(
                        text = place,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
        )
    }
}

@Composable
private fun AirportActions(
    display: AirportDetailsDisplay,
    watchArea: LocationWatchAvailability,
    placeAction: AirportPlaceAction,
    onWatchArea: () -> Unit,
    onOpenWebsite: () -> Unit,
    onOpenWikipedia: () -> Unit,
) {
    val hasPosition = display.latitude != null && display.longitude != null
    if (!hasPosition && display.website == null && display.wikipedia == null) return

    val unavailableMsg = when (watchArea) {
        LocationWatchAvailability.AVAILABLE, LocationWatchAvailability.TIER_EXCLUDED -> null
        LocationWatchAvailability.REVOKED -> R.string.server_error_revoked_msg
        LocationWatchAvailability.RESTRICTED -> R.string.upgrade_restricted_msg
        LocationWatchAvailability.NOT_LOADED,
        LocationWatchAvailability.SESSION_UNUSABLE -> R.string.airport_details_watch_area_unknown_msg
    }

    // Four labelled buttons side by side don't fit a phone's width, cells with the label below the icon do
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (hasPosition) {
            when (placeAction) {
                is AirportPlaceAction.CenterOnAirport -> ActionCell(
                    icon = Icons.TwoTone.CenterFocusStrong,
                    label = stringResource(R.string.airport_details_center_action),
                    onClick = placeAction.onClick,
                    modifier = Modifier.weight(1f),
                )

                is AirportPlaceAction.ShowOnMap -> ActionCell(
                    icon = Icons.TwoTone.Map,
                    label = stringResource(R.string.common_show_on_map_action),
                    onClick = placeAction.onClick,
                    modifier = Modifier.weight(1f),
                )
            }
            ActionCell(
                icon = Icons.TwoTone.Radar,
                label = stringResource(R.string.airport_details_watch_area_action),
                onClick = onWatchArea,
                enabled = unavailableMsg == null,
                upgrade = watchArea == LocationWatchAvailability.TIER_EXCLUDED,
                modifier = Modifier.weight(1f),
            )
        }
        if (display.website != null) {
            ActionCell(
                icon = Icons.TwoTone.Language,
                label = stringResource(R.string.airport_details_website_action),
                onClick = onOpenWebsite,
                modifier = Modifier.weight(1f),
            )
        }
        if (display.wikipedia != null) {
            ActionCell(
                icon = Icons.AutoMirrored.TwoTone.MenuBook,
                label = stringResource(R.string.airport_details_wikipedia_action),
                onClick = onOpenWikipedia,
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (hasPosition && unavailableMsg != null) {
        Text(
            text = stringResource(unavailableMsg),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** [upgrade] marks an action that opens the upgrade instead, for the installation's tier. */
@Composable
private fun ActionCell(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    upgrade: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val upgradeLabel = stringResource(R.string.common_upgrade_action)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = upgradeLabel.takeIf { upgrade },
                onClick = onClick,
            )
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Box(
                modifier = Modifier
                    .background(
                        color = if (enabled) colors.secondaryContainer else colors.onSurface.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(16.dp),
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) colors.onSecondaryContainer else colors.onSurface.copy(alpha = 0.38f),
                    modifier = Modifier.size(24.dp),
                )
            }
            if (upgrade) {
                // The click label tells accessibility services what it opens
                Icon(
                    imageVector = Icons.TwoTone.Stars,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .size(16.dp),
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.38f),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private data class Fact(
    val label: String,
    val value: String,
    val mono: Boolean = false,
    val scheduledService: Boolean = false,
)

@Composable
private fun AirportFacts(
    display: AirportDetailsDisplay,
    modifier: Modifier = Modifier,
) {
    val type = display.type?.let {
        when (it.size) {
            Airport.Size.LARGE -> stringResource(R.string.airport_type_large)
            Airport.Size.MEDIUM -> stringResource(R.string.airport_type_medium)
            Airport.Size.SMALL -> stringResource(R.string.airport_type_small)
            Airport.Size.OTHER -> it.raw
        }
    }
    val elevation = display.elevationFt?.let { LocalUnitFormatter.current.altitude(it).text() }
    val lat = display.latitude
    val lon = display.longitude
    val coordinates = if (lat != null && lon != null) "%.4f, %.4f".format(Locale.ROOT, lat, lon) else null
    val codes = display.codes.mapNotNull { code ->
        code.kind.factLabel?.let { Fact(stringResource(it), code.value, mono = true) }
    }

    val facts = listOfNotNull(
        type?.let {
            Fact(stringResource(R.string.airport_details_type_label), it, scheduledService = display.scheduledService == true)
        },
        elevation?.let { Fact(stringResource(R.string.airport_details_elevation_label), it) },
        coordinates?.let { Fact(stringResource(R.string.airport_details_coordinates_label), it) },
        display.region?.let { Fact(stringResource(R.string.airport_details_region_label), it) },
    ) + codes
    if (facts.isEmpty()) return

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        facts.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                row.forEach { FactCell(it, modifier = Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FactCell(fact: Fact, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = fact.label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // The badge goes below a value too long to share its line
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = fact.value,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = if (fact.mono) FontFamily.Monospace else null,
                ),
            )
            if (fact.scheduledService) ScheduledServiceBadge()
        }
    }
}

/** The facts' label for a code, null for the codes the glance shows. */
private val AirportDetailsDisplay.CodeKind.factLabel: Int?
    get() = when (this) {
        AirportDetailsDisplay.CodeKind.IATA, AirportDetailsDisplay.CodeKind.ICAO -> null
        AirportDetailsDisplay.CodeKind.IDENT -> R.string.airport_details_ident_label
        AirportDetailsDisplay.CodeKind.LOCAL -> R.string.airport_details_local_label
    }

@Preview2
@Composable
private fun AirportDetailsSheetContentPreview() {
    PreviewWrapper {
        AirportDetailsSheetContent(
            display = AirportDetailsDisplay.from(
                AirportTarget.Full(
                    Airport(
                        id = "oa:2212",
                        name = "Frankfurt am Main Airport",
                        type = "large",
                        ident = "EDDF",
                        latitude = 50.033333,
                        longitude = 8.570556,
                        municipality = "Frankfurt am Main",
                        country = "DE",
                        region = "DE-HE",
                        icao = "EDDF",
                        iata = "FRA",
                        elevationFt = 364,
                        scheduledService = true,
                        website = "https://www.frankfurt-airport.de/",
                        wikipedia = "https://en.wikipedia.org/wiki/Frankfurt_Airport",
                    )
                )
            ),
            lookingUp = false,
            watchArea = LocationWatchAvailability.TIER_EXCLUDED,
            placeAction = AirportPlaceAction.CenterOnAirport {},
            onWatchArea = {},
            onOpenWebsite = {},
            onOpenWikipedia = {},
            progress = { 0f },
            topInset = 0,
            maxPhotoHeight = 0,
            onCollapsedHeight = {},
        )
    }
}
