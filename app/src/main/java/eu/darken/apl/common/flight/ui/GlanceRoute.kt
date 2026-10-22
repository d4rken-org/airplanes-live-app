package eu.darken.apl.common.flight.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.darken.apl.common.flight.FlightRoute
import eu.darken.apl.common.flight.RouteProgress

/** Origin and destination codes joined by a line with the aircraft on it, their names below. */
@Composable
fun GlanceRoute(route: FlightRoute, progress: RouteProgress, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        RouteTrack(
            originLabel = route.origin?.displayLabel,
            destinationLabel = route.destination?.displayLabel,
            routeKey = listOf(route.origin?.icaoCode, route.destination?.icaoCode, route.callsign),
            progress = progress,
        )
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
