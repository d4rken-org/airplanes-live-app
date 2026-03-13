package eu.darken.apl.ar.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.darken.apl.ar.core.ScreenProjection
import eu.darken.apl.common.compose.Preview2
import eu.darken.apl.common.compose.PreviewWrapper
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ArRadar(
    blips: List<ArViewModel.RadarBlip>,
    headingDeg: Float,
    sensorAvailable: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val radius = size.minDimension / 2f
            val blipRadiusPx = 2.5.dp.toPx()
            val thinStroke = 1.dp.toPx()

            // Background circle
            drawCircle(
                color = Color.Black.copy(alpha = 0.55f),
                radius = radius,
                center = Offset(cx, cy),
            )

            // Range rings at 33% and 66%
            val ringAlpha = Color.White.copy(alpha = 0.15f)
            drawCircle(ringAlpha, radius * 0.33f, Offset(cx, cy), style = Stroke(thinStroke))
            drawCircle(ringAlpha, radius * 0.66f, Offset(cx, cy), style = Stroke(thinStroke))

            // FOV cone (only when sensor is available)
            if (sensorAvailable) {
                val sweepDeg = ScreenProjection.H_FOV_DEG
                val startAngle = headingDeg - sweepDeg / 2f - 90f
                drawArc(
                    color = Color.White.copy(alpha = 0.12f),
                    startAngle = startAngle,
                    sweepAngle = sweepDeg,
                    useCenter = true,
                    topLeft = Offset(cx - radius, cy - radius),
                    size = Size(radius * 2, radius * 2),
                )
            }

            // Aircraft blips
            for (blip in blips) {
                val bx = cx + sin(blip.bearingRad) * blip.fractionOfRange * radius
                val by = cy - cos(blip.bearingRad) * blip.fractionOfRange * radius
                drawCircle(
                    color = Color.White,
                    radius = blipRadiusPx,
                    center = Offset(bx, by),
                )
            }

            // Outer border
            drawCircle(
                color = Color.White.copy(alpha = 0.3f),
                radius = radius,
                center = Offset(cx, cy),
                style = Stroke(thinStroke),
            )
        }

        // North indicator
        Text(
            text = "N",
            color = Color.White,
            fontSize = 10.sp,
            lineHeight = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 4.dp),
        )
    }
}

@Preview2
@Composable
private fun ArRadarPreview() {
    PreviewWrapper {
        ArRadar(
            blips = listOf(
                ArViewModel.RadarBlip(bearingRad = 0.3f, fractionOfRange = 0.4f),
                ArViewModel.RadarBlip(bearingRad = 1.8f, fractionOfRange = 0.7f),
                ArViewModel.RadarBlip(bearingRad = -1.2f, fractionOfRange = 0.25f),
                ArViewModel.RadarBlip(bearingRad = 2.9f, fractionOfRange = 0.9f),
            ),
            headingDeg = 45f,
            sensorAvailable = true,
            modifier = Modifier.size(120.dp),
        )
    }
}
