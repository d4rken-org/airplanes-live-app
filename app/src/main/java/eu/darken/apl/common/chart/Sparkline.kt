package eu.darken.apl.common.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun Sparkline(
    data: List<ChartPoint>,
    lineColor: Color,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Unspecified,
    strokeWidth: Float = 2f,
) {
    if (data.size < 2) {
        Box(modifier)
        return
    }

    val sorted = remember(data) { data.sortedBy { it.timestamp } }
    val xMin = remember(sorted) { sorted.first().timestamp.toEpochMilli().toFloat() }
    val xRange = remember(sorted) {
        (sorted.last().timestamp.toEpochMilli() - sorted.first().timestamp.toEpochMilli()).toFloat()
    }
    val yMin = remember(sorted) { sorted.minOf { it.value }.toFloat() }
    val yRange = remember(sorted) {
        val range = (sorted.maxOf { it.value } - sorted.minOf { it.value }).toFloat()
        if (range == 0f) 1f else range
    }

    val content = @Composable { canvasModifier: Modifier ->
        Canvas(modifier = canvasModifier) {
            drawSparkline(sorted, xMin, xRange, yMin, yRange, lineColor, backgroundColor, strokeWidth)
        }
    }

    if (backgroundColor != Color.Unspecified) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(4.dp))
                .background(backgroundColor)
                .padding(1.dp),
        ) {
            content(Modifier.matchParentSize())
        }
    } else {
        content(modifier)
    }
}

private fun DrawScope.drawSparkline(
    sorted: List<ChartPoint>,
    xMin: Float,
    xRange: Float,
    yMin: Float,
    yRange: Float,
    lineColor: Color,
    backgroundColor: Color,
    strokeWidth: Float,
) {
    if (xRange <= 0f) return

    val w = size.width
    val h = size.height
    val pad = strokeWidth

    fun xOf(point: ChartPoint): Float {
        return ((point.timestamp.toEpochMilli() - xMin) / xRange) * w
    }

    fun yOf(point: ChartPoint): Float {
        return h - pad - ((point.value.toFloat() - yMin) / yRange) * (h - pad * 2)
    }

    val linePath = Path().apply {
        moveTo(xOf(sorted.first()), yOf(sorted.first()))
        for (i in 1 until sorted.size) {
            lineTo(xOf(sorted[i]), yOf(sorted[i]))
        }
    }

    if (backgroundColor != Color.Unspecified) {
        val fillPath = Path().apply {
            addPath(linePath)
            lineTo(xOf(sorted.last()), h)
            lineTo(xOf(sorted.first()), h)
            close()
        }
        drawPath(
            path = fillPath,
            color = lineColor.copy(alpha = 0.15f),
            style = Fill,
        )
    }

    drawPath(
        path = linePath,
        color = lineColor,
        style = Stroke(width = strokeWidth),
    )
}
