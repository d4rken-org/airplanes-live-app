package eu.darken.apl.common.flight.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Flight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import eu.darken.apl.R
import eu.darken.apl.common.compose.Preview2
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.common.flight.RouteProgress
import kotlin.math.roundToInt

/** Origin and destination codes joined by a line, the plane on it where the aircraft is. */
@Composable
fun RouteTrack(
    originLabel: String,
    destinationLabel: String,
    routeKey: Any,
    progress: RouteProgress,
    modifier: Modifier = Modifier,
) {
    val target = progress.trackTarget()
    val position = remember { Animatable(target.fraction) }
    var shown by remember { mutableStateOf(TrackState(routeKey, progress)) }
    LaunchedEffect(routeKey, progress) {
        val next = TrackState(routeKey, progress)
        val glide = glides(shown, next)
        shown = next
        // A newer update cancels this one, its animation then starts from wherever the plane is drawn
        if (glide) position.animateTo(target.fraction, tween(GLIDE_MILLIS, easing = FastOutSlowInEasing))
        else position.snapTo(target.fraction)
    }

    val description = when (progress) {
        RouteProgress.AtOrigin -> stringResource(R.string.route_progress_at_origin, originLabel)
        RouteProgress.AtDestination -> stringResource(R.string.route_progress_at_destination, destinationLabel)
        is RouteProgress.Airborne -> stringResource(R.string.route_progress_airborne, originLabel, destinationLabel)
        RouteProgress.Unknown -> stringResource(R.string.route_progress_unknown, originLabel, destinationLabel)
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Text(text = originLabel, style = MaterialTheme.typography.titleMedium)
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        ) {
            HorizontalDivider(modifier = Modifier.fillMaxWidth())
            Icon(
                Icons.TwoTone.Flight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints.copy(minWidth = 0))
                        val travel = (constraints.maxWidth - placeable.width).coerceAtLeast(0)
                        layout(constraints.maxWidth, placeable.height) {
                            // Relative placement mirrors this in RTL, the origin stays at the start
                            placeable.placeRelative((travel * position.value).roundToInt(), 0)
                        }
                    }
                    .size(16.dp)
                    .rotate(if (rtl) -90f else 90f)
                    .alpha(if (target.dimmed) DIMMED_ALPHA else 1f),
            )
        }
        Text(text = destinationLabel, style = MaterialTheme.typography.titleMedium)
    }
}

internal data class TrackTarget(val fraction: Float, val dimmed: Boolean)

internal data class TrackState(val routeKey: Any, val progress: RouteProgress)

internal fun RouteProgress.trackTarget(): TrackTarget = when (this) {
    RouteProgress.AtOrigin -> TrackTarget(0f, dimmed = false)
    RouteProgress.AtDestination -> TrackTarget(1f, dimmed = false)
    is RouteProgress.Airborne -> TrackTarget(fraction.coerceIn(0f, 1f), dimmed = false)
    RouteProgress.Unknown -> TrackTarget(0.5f, dimmed = true)
}

/** Only observed movement in flight glides; a new route, landing, or losing the position jumps. */
internal fun glides(from: TrackState, to: TrackState): Boolean =
    from.routeKey == to.routeKey && from.progress is RouteProgress.Airborne && to.progress is RouteProgress.Airborne

private const val GLIDE_MILLIS = 800
private const val DIMMED_ALPHA = 0.38f

@Preview2
@Composable
private fun RouteTrackPreview() {
    PreviewWrapper {
        RouteTrack(
            originLabel = "FRA",
            destinationLabel = "LHR",
            routeKey = "EDDF-EGLL",
            progress = RouteProgress.Airborne(0.3f),
        )
    }
}
