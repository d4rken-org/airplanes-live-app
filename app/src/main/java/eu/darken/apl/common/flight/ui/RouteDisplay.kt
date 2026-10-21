package eu.darken.apl.common.flight.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
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

@Preview2
@Composable
private fun RouteDisplayPreview() {
    PreviewWrapper { RouteDisplay(route = mockFlightRoute()) }
}
