package eu.darken.apl.map.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * The followed aircraft as the map draws its selection, in pixels of the map view. The map places its
 * sources a frame or more after the camera moved, so a followed aircraft drawn by it lags behind the
 * map; drawn over it at the camera's centre it stays put.
 */
internal data class FollowedMark(
    val hex: String,
    val center: Offset,
    val ringRadius: Float,
    val shadowRadius: Float,
    /** The outer part of [shadowRadius] the shadow fades over, as MapLibre's circle blur. */
    val shadowBlur: Float,
    val silhouette: ImageBitmap,
    val iconWidth: Float,
    val iconHeight: Float,
    val rotation: Float,
    val color: Int,
    val iconAlpha: Float,
    val label: String,
    /** From [center] down to the label's top. */
    val labelTop: Float,
    val badges: List<Badge>,
) {
    /** What a tap can hit: the ring or the turned icon, not the shadow or the label. */
    val reach: Float get() = maxOf(ringRadius, hypot(iconWidth, iconHeight) / 2f)

    data class Badge(
        val image: ImageBitmap,
        val width: Float,
        val height: Float,
        /** From the mark's centre to the badge's. */
        val offset: Offset,
    ) {
        /** Whether [x], [y] lies on the badge, which reaches past the ring. */
        fun hit(center: Offset, x: Float, y: Float): Boolean =
            hypot(center.x + offset.x - x, center.y + offset.y - y) <= width / 2f
    }
}

/** Only read while drawing, so a new mark every display frame redraws without recomposing. */
@Composable
internal fun FollowedMarkOverlay(mark: State<FollowedMark?>, modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density
    val text = remember(density) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = SELECTED_LABEL_SIZE * density
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT
            color = android.graphics.Color.BLACK
        }
    }
    // The map's halo width reaches out from the glyph's edge, a stroke reaches both ways
    val halo = remember(density) {
        Paint(text).apply {
            style = Paint.Style.STROKE
            strokeWidth = SELECTED_LABEL_HALO * 2 * density
            strokeJoin = Paint.Join.ROUND
            color = android.graphics.Color.WHITE
        }
    }
    val ascent = remember(text) { text.fontMetrics.ascent }
    Canvas(modifier) {
        val current = mark.value ?: return@Canvas
        // MapLibre 13.6 blurs a circle with a smoothstep over the outer part of its radius
        val shadowStart = 1f - current.shadowBlur
        drawCircle(
            brush = Brush.radialGradient(
                *SHADOW_STEPS.map { step ->
                    val opacity = 1f - step * step * (3f - 2f * step)
                    (shadowStart + (1f - shadowStart) * step) to Color.Black.copy(alpha = SELECTED_SHADOW_OPACITY * opacity)
                }.toTypedArray(),
                center = current.center,
                radius = current.shadowRadius,
            ),
            radius = current.shadowRadius,
            center = current.center,
        )
        drawCircle(Color.White.copy(alpha = SELECTED_RING_OPACITY), radius = current.ringRadius, center = current.center)
        rotate(current.rotation, pivot = current.center) {
            drawImage(
                image = current.silhouette,
                dstOffset = IntOffset(
                    (current.center.x - current.iconWidth / 2f).roundToInt(),
                    (current.center.y - current.iconHeight / 2f).roundToInt(),
                ),
                dstSize = IntSize(current.iconWidth.roundToInt(), current.iconHeight.roundToInt()),
                alpha = current.iconAlpha,
                colorFilter = ColorFilter.tint(Color(current.color), BlendMode.SrcIn),
                // Far out the silhouette is drawn at under half its size
                filterQuality = FilterQuality.High,
            )
        }
        current.badges.forEach { badge ->
            drawImage(
                image = badge.image,
                dstOffset = IntOffset(
                    (current.center.x + badge.offset.x - badge.width / 2f).roundToInt(),
                    (current.center.y + badge.offset.y - badge.height / 2f).roundToInt(),
                ),
                dstSize = IntSize(badge.width.roundToInt(), badge.height.roundToInt()),
                alpha = current.iconAlpha,
                filterQuality = FilterQuality.High,
            )
        }
        drawIntoCanvas { canvas ->
            val baseline = current.center.y + current.labelTop - ascent
            canvas.nativeCanvas.drawText(current.label, current.center.x, baseline, halo)
            canvas.nativeCanvas.drawText(current.label, current.center.x, baseline, text)
        }
    }
}

internal const val SELECTED_RING_OPACITY = 0.7f
internal const val SELECTED_SHADOW_OPACITY = 0.25f
private val SHADOW_STEPS = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
internal const val SELECTED_LABEL_SIZE = 12f
internal const val SELECTED_LABEL_HALO = 2f
