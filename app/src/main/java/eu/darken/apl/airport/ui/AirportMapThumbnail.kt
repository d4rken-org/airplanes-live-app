package eu.darken.apl.airport.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.LocalAirport
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import eu.darken.apl.airport.core.Airport
import eu.darken.apl.airport.core.thumbnailZoom
import eu.darken.apl.watch.ui.thumbnail.MapPointThumbnailSpec
import eu.darken.apl.watch.ui.thumbnail.WatchTypeTile

/** The airport on a map, the airport tile while it loads, when it can't be drawn or its position is unknown. */
@Composable
fun AirportMapThumbnail(
    latitude: Double?,
    longitude: Double?,
    size: Airport.Size,
    spec: MapPointThumbnailSpec,
    modifier: Modifier = Modifier,
) {
    if (latitude == null || longitude == null) {
        Box(modifier = modifier) { AirportTile() }
        return
    }

    val context = LocalContext.current
    val density = LocalDensity.current.density
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val zoom = size.thumbnailZoom()
    val request = remember(latitude, longitude, zoom, spec, density, darkTheme) {
        ImageRequest.Builder(context)
            .data(
                spec.query(
                    latitude = latitude,
                    longitude = longitude,
                    zoom = zoom,
                    density = density,
                    darkTheme = darkTheme,
                )
            )
            // The slot resizes while a sheet grows, a fixed size keeps the request and its memory cache entry
            .size(Size.ORIGINAL)
            .build()
    }
    var isLoaded by remember(request) { mutableStateOf(false) }

    Box(modifier = modifier) {
        if (!isLoaded) AirportTile()
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onSuccess = { isLoaded = true },
            onError = { isLoaded = false },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun AirportTile() {
    WatchTypeTile(icon = Icons.TwoTone.LocalAirport, modifier = Modifier.fillMaxSize())
}
