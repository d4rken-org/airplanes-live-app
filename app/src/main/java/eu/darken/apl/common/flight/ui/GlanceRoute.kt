package eu.darken.apl.common.flight.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.darken.apl.R
import eu.darken.apl.common.flight.Airport
import eu.darken.apl.common.flight.FlightRoute
import eu.darken.apl.common.flight.RouteProgress

/**
 * Origin and destination codes joined by a line with the aircraft on it, their names below.
 * With [onAirportClick] each known airport is a link: its name, or its code when it has no name.
 */
@Composable
fun GlanceRoute(
    route: FlightRoute,
    progress: RouteProgress,
    modifier: Modifier = Modifier,
    onAirportClick: ((Airport) -> Unit)? = null,
) {
    val origin = route.origin
    val destination = route.destination
    val originName = origin?.let { it.name ?: it.countryName }
    val destinationName = destination?.let { it.name ?: it.countryName }
    val originClick: (() -> Unit)? = if (onAirportClick != null && origin != null) {
        { onAirportClick(origin) }
    } else {
        null
    }
    val destinationClick: (() -> Unit)? = if (onAirportClick != null && destination != null) {
        { onAirportClick(destination) }
    } else {
        null
    }
    val originClickLabel = origin?.clickLabel()
    val destinationClickLabel = destination?.clickLabel()

    Column(modifier = modifier) {
        RouteTrack(
            originLabel = origin?.displayLabel,
            destinationLabel = destination?.displayLabel,
            routeKey = listOf(origin?.icaoCode, destination?.icaoCode, route.callsign),
            progress = progress,
            onOriginClick = originClick,
            originClickLabel = originClickLabel.takeIf { originName == null },
            onDestinationClick = destinationClick,
            destinationClickLabel = destinationClickLabel.takeIf { destinationName == null },
        )
        if (originName != null || destinationName != null) {
            Row {
                AirportName(
                    text = originName,
                    onClick = originClick,
                    clickLabel = originClickLabel,
                    end = false,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(12.dp))
                AirportName(
                    text = destinationName,
                    onClick = destinationClick,
                    clickLabel = destinationClickLabel,
                    end = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun AirportName(
    text: String?,
    onClick: (() -> Unit)?,
    clickLabel: String?,
    end: Boolean,
    modifier: Modifier = Modifier,
) {
    val link = text != null && onClick != null
    Text(
        text = text ?: "",
        style = MaterialTheme.typography.bodySmall,
        color = if (link) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        textDecoration = if (link) TextDecoration.Underline else null,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = if (end) TextAlign.End else TextAlign.Unspecified,
        modifier = if (text != null && onClick != null) {
            // Only the name itself is the link, not the free space next to it
            modifier
                .wrapContentWidth(if (end) Alignment.End else Alignment.Start)
                .clickable(onClickLabel = clickLabel, role = Role.Button, onClick = onClick)
        } else {
            modifier
        },
    )
}

@Composable
private fun Airport.clickLabel(): String = stringResource(R.string.route_airport_show_details_x, name ?: displayLabel)
