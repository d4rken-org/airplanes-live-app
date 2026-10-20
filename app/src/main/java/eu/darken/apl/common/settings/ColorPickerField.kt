package eu.darken.apl.common.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import android.graphics.Color as AndroidColor

/**
 * The pointer affordance. It carries no semantics of its own - [ChannelSlider] is the route for
 * anyone not dragging a finger across it.
 */
@Composable
internal fun SaturationBrightnessField(
    modifier: Modifier = Modifier,
    picked: Hsv,
    onPickedChange: (Hsv) -> Unit,
) {
    val markerColor = MaterialTheme.colorScheme.onSurface

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(8.dp))
            .pointerInput(picked.hue) {
                fun report(position: Offset) = onPickedChange(
                    picked.copy(
                        saturation = (position.x / size.width).coerceIn(0f, 1f),
                        brightness = 1f - (position.y / size.height).coerceIn(0f, 1f),
                    )
                )
                awaitEachGesture {
                    val down = awaitFirstDown()
                    report(down.position)
                    drag(down.id) { change ->
                        change.consume()
                        report(change.position)
                    }
                }
            },
    ) {
        drawRect(Brush.horizontalGradient(listOf(Color.White, Color.hsv(picked.hue, 1f, 1f))))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        drawCircle(
            color = markerColor,
            radius = 6.dp.toPx(),
            center = Offset(size.width * picked.saturation, size.height * (1f - picked.brightness)),
            style = Stroke(width = 2.dp.toPx()),
        )
    }
}

@Composable
internal fun ChannelSlider(
    modifier: Modifier = Modifier,
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
}

internal data class Hsv(
    val hue: Float,
    val saturation: Float,
    val brightness: Float,
)

internal val HsvSaver: Saver<Hsv, Any> = listSaver(
    save = { listOf(it.hue, it.saturation, it.brightness) },
    restore = { Hsv(hue = it[0], saturation = it[1], brightness = it[2]) },
)

internal fun Int.toHsv(): Hsv {
    val channels = FloatArray(3)
    AndroidColor.colorToHSV(this or 0xFF000000.toInt(), channels)
    return Hsv(channels[0], channels[1], channels[2])
}

internal fun Hsv.toColor(): Color = Color.hsv(hue, saturation, brightness)

internal fun Hsv.toRgb(): Int = toColor().toArgb() and 0xFFFFFF
