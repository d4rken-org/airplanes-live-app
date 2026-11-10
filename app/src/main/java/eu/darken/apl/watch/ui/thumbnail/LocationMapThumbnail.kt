package eu.darken.apl.watch.ui.thumbnail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import eu.darken.apl.watch.core.types.LocationWatch
import eu.darken.apl.watch.ui.WatchListViewModel.WatchType
import eu.darken.apl.watch.ui.icon

/** The watched area on a map, the type tile while it loads or when it can't be drawn. */
@Composable
fun LocationMapThumbnail(
    watch: LocationWatch,
    spec: LocationThumbnailSpec,
    modifier: Modifier = Modifier,
    iconSize: Dp = 32.dp,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val circleColor = MaterialTheme.colorScheme.primary.toArgb()
    val latitude = watch.latitude
    val longitude = watch.longitude
    val radiusInMeters = watch.radiusInMeters
    val request = remember(latitude, longitude, radiusInMeters, spec, density, darkTheme, circleColor) {
        ImageRequest.Builder(context)
            .data(
                spec.query(
                    latitude = latitude,
                    longitude = longitude,
                    radiusInMeters = radiusInMeters,
                    density = density,
                    darkTheme = darkTheme,
                    circleColor = circleColor,
                )
            )
            // The slot resizes while a sheet grows, a fixed size keeps the request and its memory cache entry
            .size(Size.ORIGINAL)
            .build()
    }
    var isLoaded by remember(request) { mutableStateOf(false) }

    Box(modifier = modifier) {
        if (!isLoaded) {
            WatchTypeTile(icon = WatchType.LOCATION.icon, iconSize = iconSize, modifier = Modifier.fillMaxSize())
        }
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
fun WatchTypeTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    iconSize: Dp = 32.dp,
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}
