package eu.darken.apl.ar.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.darken.apl.common.compose.Preview2
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.common.planespotters.PlanespottersThumbnail
import eu.darken.apl.common.planespotters.coil.AircraftThumbnailQuery

@Composable
fun ArLabelCard(
    label: ArLabel,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .widthIn(max = 128.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(onClick = onTap),
    ) {
        Row(
            modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label.callsign?.trim() ?: label.hex,
                color = Color.White,
                fontSize = 12.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            val distNm = label.distanceM / 1852.0
            Text(
                text = "%.1fNM".format(distNm),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 9.sp,
                lineHeight = 9.sp,
                maxLines = 1,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        if (label.description != null) {
            Text(
                text = label.description,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 10.sp,
                lineHeight = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
        val parts = mutableListOf<String>()
        label.altitudeFt?.let { parts.add("${it}ft") }
        label.speedKts?.let { parts.add("${it.toInt()}kt") }
        if (parts.isNotEmpty()) {
            Text(
                text = parts.joinToString(" | "),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 9.sp,
                lineHeight = 9.sp,
                maxLines = 1,
                modifier = Modifier.padding(start = 6.dp, end = 6.dp),
            )
        }
        when (val routeState = label.routeState) {
            is RouteUiState.Ready -> {
                Text(
                    text = routeState.text,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 9.sp,
                    lineHeight = 9.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }

            is RouteUiState.Loading -> {
                val infiniteTransition = rememberInfiniteTransition(label = "routeShimmer")
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.15f,
                    targetValue = 0.4f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "shimmerAlpha",
                )
                Box(
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .size(width = 60.dp, height = 10.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = alpha)),
                )
            }

            else -> {}
        }
        Spacer(Modifier.height(18.dp))
        PlanespottersThumbnail(
            query = AircraftThumbnailQuery(hex = label.hex, registration = label.registration),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        )
    }
}

@Preview2
@Composable
private fun ArLabelCardPreview() {
    PreviewWrapper {
        ArLabelCard(
            label = ArLabel(
                hex = "4CA2B1", callsign = "RYR9313", registration = "EI-ABC",
                description = "BOEING 737-800", altitudeFt = 21100,
                speedKts = 386f, distanceM = 25000.0,
                screenXNorm = 0f, screenYNorm = 0f,
            ),
            onTap = {},
        )
    }
}

@Preview2
@Composable
private fun ArLabelCardMinimalPreview() {
    PreviewWrapper {
        ArLabelCard(
            label = ArLabel(
                hex = "A1B2C3", callsign = null, registration = null,
                description = null, altitudeFt = null,
                speedKts = null, distanceM = 9260.0,
                screenXNorm = 0f, screenYNorm = 0f,
            ),
            onTap = {},
        )
    }
}
