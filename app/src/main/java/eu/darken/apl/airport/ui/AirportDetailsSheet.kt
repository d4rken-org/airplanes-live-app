package eu.darken.apl.airport.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.apl.R
import eu.darken.apl.airport.core.Airport
import eu.darken.apl.airport.core.AirportTarget
import eu.darken.apl.common.compose.Preview2
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.common.error.ErrorEventHandler
import eu.darken.apl.common.navigation.NavigationEventHandler
import eu.darken.apl.common.settings.UpgradeChip
import eu.darken.apl.common.units.LocalUnitFormatter
import eu.darken.apl.common.units.text
import eu.darken.apl.watch.core.LocationWatchAvailability
import java.util.Locale

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
    val initial = remember(target) {
        AirportDetailsViewModel.State(
            display = AirportDetailsDisplay.from(target),
            lookingUp = target is AirportTarget.Partial,
        )
    }
    val current = state ?: initial

    ModalBottomSheet(onDismissRequest = { vm.navUp() }) {
        AirportDetailsSheetContent(
            display = current.display,
            lookingUp = current.lookingUp,
            watchArea = watchArea,
            onShowOnMap = { vm.showOnMap() },
            onWatchArea = { vm.watchArea() },
            onOpenWebsite = { vm.openWebsite() },
            onOpenWikipedia = { vm.openWikipedia() },
        )
    }
}

@Composable
internal fun AirportDetailsSheetContent(
    display: AirportDetailsDisplay,
    lookingUp: Boolean,
    watchArea: LocationWatchAvailability,
    onShowOnMap: () -> Unit,
    onWatchArea: () -> Unit,
    onOpenWebsite: () -> Unit,
    onOpenWikipedia: () -> Unit,
) {
    val type = display.type?.let {
        when (it.size) {
            Airport.Size.LARGE -> stringResource(R.string.airport_type_large)
            Airport.Size.MEDIUM -> stringResource(R.string.airport_type_medium)
            Airport.Size.SMALL -> stringResource(R.string.airport_type_small)
            Airport.Size.OTHER -> it.raw
        }
    }
    val lat = display.latitude
    val lon = display.longitude
    val coordinates = if (lat != null && lon != null) "%.4f, %.4f".format(Locale.ROOT, lat, lon) else null
    val lookupDescription = stringResource(R.string.airport_details_lookup_progress)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp),
    ) {
        Row(
            modifier = Modifier.padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = display.title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
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

        display.codes.forEach { DetailRow(stringResource(it.kind.label), it.value, mono = true) }
        display.municipality?.let { DetailRow(stringResource(R.string.airport_details_municipality_label), it) }
        display.region?.let { DetailRow(stringResource(R.string.airport_details_region_label), it) }
        display.country?.let { DetailRow(stringResource(R.string.airport_details_country_label), it) }
        type?.let {
            DetailRow(
                label = stringResource(R.string.airport_details_type_label),
                value = it,
                trailing = if (display.scheduledService == true) {
                    { ScheduledServiceBadge() }
                } else {
                    null
                },
            )
        }
        display.elevationFt?.let {
            DetailRow(
                label = stringResource(R.string.airport_details_elevation_label),
                value = LocalUnitFormatter.current.altitude(it).text(),
            )
        }
        coordinates?.let { DetailRow(stringResource(R.string.airport_details_coordinates_label), it) }

        if (coordinates != null || display.website != null || display.wikipedia != null) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (coordinates != null) {
                    Button(
                        onClick = onShowOnMap,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.common_show_on_map_action))
                    }
                    WatchAreaAction(watchArea, onWatchArea)
                }
                if (display.website != null) {
                    OutlinedButton(
                        onClick = onOpenWebsite,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.airport_details_website_action))
                    }
                }
                if (display.wikipedia != null) {
                    OutlinedButton(
                        onClick = onOpenWikipedia,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.airport_details_wikipedia_action))
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchAreaAction(
    availability: LocationWatchAvailability,
    onClick: () -> Unit,
) {
    val unavailableMsg = when (availability) {
        LocationWatchAvailability.AVAILABLE, LocationWatchAvailability.TIER_EXCLUDED -> null
        LocationWatchAvailability.REVOKED -> R.string.server_error_revoked_msg
        LocationWatchAvailability.RESTRICTED -> R.string.upgrade_restricted_msg
        LocationWatchAvailability.NOT_LOADED,
        LocationWatchAvailability.SESSION_UNUSABLE -> R.string.airport_details_watch_area_unknown_msg
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton(
            onClick = onClick,
            enabled = unavailableMsg == null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.airport_details_watch_area_action))
            if (availability == LocationWatchAvailability.TIER_EXCLUDED) {
                Spacer(Modifier.width(8.dp))
                UpgradeChip(onClick = onClick)
            }
        }
        unavailableMsg?.let {
            Text(
                text = stringResource(it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ScheduledServiceBadge() {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = stringResource(R.string.airport_details_scheduled_service_badge),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

private val AirportDetailsDisplay.CodeKind.label: Int
    get() = when (this) {
        AirportDetailsDisplay.CodeKind.IATA -> R.string.airport_details_iata_label
        AirportDetailsDisplay.CodeKind.ICAO -> R.string.airport_details_icao_label
        AirportDetailsDisplay.CodeKind.IDENT -> R.string.airport_details_ident_label
        AirportDetailsDisplay.CodeKind.LOCAL -> R.string.airport_details_local_label
    }

@Composable
private fun DetailRow(
    label: String,
    value: String,
    mono: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .weight(0.35f)
                .padding(end = 8.dp),
        )
        Row(
            modifier = Modifier.weight(0.65f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = if (mono) FontFamily.Monospace else null,
                ),
                modifier = Modifier.weight(1f, fill = false),
            )
            trailing?.invoke()
        }
    }
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
            watchArea = LocationWatchAvailability.AVAILABLE,
            onShowOnMap = {},
            onWatchArea = {},
            onOpenWebsite = {},
            onOpenWikipedia = {},
        )
    }
}
