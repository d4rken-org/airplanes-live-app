package eu.darken.apl.main.ui

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.darken.apl.R
import eu.darken.apl.common.flight.FlightRoute
import eu.darken.apl.common.flight.ui.RouteDisplay
import eu.darken.apl.common.planespotters.PlanespottersMeta
import eu.darken.apl.common.planespotters.PlanespottersThumbnail
import eu.darken.apl.common.planespotters.coil.AircraftThumbnailQuery
import eu.darken.apl.common.planespotters.toPlanespottersQuery
import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.main.core.aircraft.messageTypeLabel
import java.time.Instant

@Composable
fun AircraftDetails(
    aircraft: Aircraft,
    route: FlightRoute? = null,
    distanceInMeter: Float? = null,
    onThumbnailClick: ((PlanespottersMeta) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        // Airframe
        Text(
            text = aircraft.description
                ?: stringResource(R.string.aircraft_details_description_unknown),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 4.dp),
        )

        // Operator + distance
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = aircraft.operator
                    ?: stringResource(R.string.aircraft_details_operator_unknown),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (distanceInMeter != null) {
                Text(
                    text = stringResource(
                        R.string.general_xdistance_away_label,
                        "${(distanceInMeter / 1000).toInt()} km",
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 16.dp),
                )
            }
        }

        // Route
        if (route != null) {
            RouteDisplay(
                route = route,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp),
            )
        }

        // Thumbnail + info grid side by side
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            PlanespottersThumbnail(
                query = aircraft.toPlanespottersQuery(large = true),
                onImageClick = onThumbnailClick,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
            )

            // Info column: last seen, 3x2 grid, source
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // Last seen
                Text(
                    text = DateUtils.getRelativeTimeSpanString(
                        aircraft.seenAt.toEpochMilli(),
                        Instant.now().toEpochMilli(),
                        DateUtils.MINUTE_IN_MILLIS,
                    ).toString(),
                    style = MaterialTheme.typography.labelMedium,
                )

                // 3x2 info grid
                InfoGrid(aircraft)

                // Source
                Text(
                    text = stringResource(
                        R.string.aircraft_details_datasource_x,
                        aircraft.messageTypeLabel,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun InfoGrid(aircraft: Aircraft) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        // Row 1: Callsign | Registration
        Row(modifier = Modifier.fillMaxWidth()) {
            InfoCell(
                value = aircraft.callsign?.takeIf { it.isNotBlank() } ?: "?",
                label = stringResource(R.string.common_callsign_label),
                modifier = Modifier.weight(1f),
            )
            InfoCell(
                value = aircraft.registration ?: "?",
                label = stringResource(R.string.common_registration_label),
                modifier = Modifier.weight(1f),
            )
        }
        // Row 2: Hex | Squawk
        Row(modifier = Modifier.fillMaxWidth()) {
            InfoCell(
                value = "#${aircraft.hex.uppercase()}",
                label = stringResource(R.string.common_hex_label),
                modifier = Modifier.weight(1f),
            )
            InfoCell(
                value = aircraft.squawk ?: "?",
                label = stringResource(R.string.common_squawk_label),
                modifier = Modifier.weight(1f),
                isAlert = aircraft.squawk?.startsWith("7") == true,
            )
        }
        // Row 3: Altitude | Speed
        Row(modifier = Modifier.fillMaxWidth()) {
            InfoCell(
                value = "${aircraft.altitude ?: "?"} ft",
                label = stringResource(R.string.common_altitude_label),
                modifier = Modifier.weight(1f),
            )
            InfoCell(
                value = "${aircraft.indicatedAirSpeed ?: aircraft.groundSpeed ?: "?"} kts",
                label = stringResource(R.string.common_speed_label),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun InfoCell(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    isAlert: Boolean = false,
) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = if (isAlert) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
