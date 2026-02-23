package eu.darken.apl.common.flight.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.darken.apl.common.compose.Preview2
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.common.compose.preview.mockFlightRoute
import eu.darken.apl.common.flight.FlightRoute

@Composable
fun RouteDisplay(
    route: FlightRoute,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = "\u2191 ${route.origin?.routeDisplayText ?: "?"}",
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "\u2193 ${route.destination?.routeDisplayText ?: "?"}",
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun HorizontalRouteBar(route: FlightRoute, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f))
        Text(
            text = route.origin?.displayLabel ?: "?",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "──✈──",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = route.destination?.displayLabel ?: "?",
            style = MaterialTheme.typography.titleMedium,
        )
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
}

@Preview2
@Composable
private fun RouteDisplayPreview() {
    PreviewWrapper { RouteDisplay(route = mockFlightRoute()) }
}

@Preview2
@Composable
private fun HorizontalRouteBarPreview() {
    PreviewWrapper { HorizontalRouteBar(route = mockFlightRoute()) }
}
