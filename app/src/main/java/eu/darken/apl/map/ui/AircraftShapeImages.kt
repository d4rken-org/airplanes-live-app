package eu.darken.apl.map.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import androidx.core.graphics.PathParser
import androidx.core.graphics.createBitmap
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.map.core.AircraftShapes
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal fun shapeImageName(shape: String) = "apl-shape-$shape"

/**
 * White silhouettes as distance fields the map tints by altitude. The fill carries tar1090's
 * outline width, which keeps thin wings visible and the shapes as large as on the website.
 */
internal fun renderShapeImages(shapes: AircraftShapes, density: Float): Map<String, Bitmap> =
    shapes.shapes.mapNotNull { (name, shape) ->
        try {
            shapeImageName(name) to render(shape, density).toDistanceField()
        } catch (e: RuntimeException) {
            log(TAG) { "Shape $name could not be drawn: $e" }
            null
        }
    }.toMap()

private fun render(shape: AircraftShapes.Shape, density: Float): Bitmap {
    val width = ceil(shape.w * DP_PER_TAR1090_PX * density).toInt().coerceAtLeast(1)
    val height = ceil(shape.h * DP_PER_TAR1090_PX * density).toInt().coerceAtLeast(1)
    val (minX, minY, boxWidth, boxHeight) = shape.viewBox
    var scaleX = width / boxWidth
    var scaleY = height / boxHeight
    if (!shape.stretch) {
        scaleX = min(scaleX, scaleY)
        scaleY = scaleX
    }

    val bitmap = createBitmap(width, height)
    val canvas = Canvas(bitmap)
    // Centred like an SVG viewBox with its default aspect ratio handling
    canvas.translate((width - boxWidth * scaleX) / 2f, (height - boxHeight * scaleY) / 2f)
    canvas.scale(scaleX, scaleY)
    canvas.translate(-minX, -minY)
    shape.matrix?.let { (a, b, c, d, e, f) ->
        canvas.concat(Matrix().apply { setValues(floatArrayOf(a, c, e, b, d, f, 0f, 0f, 1f)) })
    }

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL_AND_STROKE
        strokeJoin = Paint.Join.ROUND
        strokeWidth = OUTLINE_WIDTH * shape.strokeScale
    }
    shape.paths.forEach { canvas.drawPath(PathParser.createPathFromPathData(it), paint) }
    return bitmap
}

private operator fun <T> List<T>.component6(): T = this[5]

/**
 * MapLibre reads an SDF image's alpha as the distance to the outline, the way its glyphs are made:
 * 191 on the outline, 32 more per pixel inside and 32 less per pixel outside.
 *
 * Ported from mapbox/tiny-sdf, Copyright (c) 2016-2024 Mapbox, Inc., BSD-2-Clause.
 */
internal fun Bitmap.toDistanceField(): Bitmap {
    val gridWidth = width + 2 * SDF_BUFFER
    val gridHeight = height + 2 * SDF_BUFFER
    val coverage = IntArray(width * height).also { getPixels(it, 0, width, 0, 0, width, height) }
    val outer = DoubleArray(gridWidth * gridHeight) { FAR }
    val inner = DoubleArray(gridWidth * gridHeight)
    for (y in 0 until height) for (x in 0 until width) {
        val a = Color.alpha(coverage[y * width + x]) / 255.0
        if (a == 0.0) continue
        val i = (y + SDF_BUFFER) * gridWidth + x + SDF_BUFFER
        if (a == 1.0) {
            outer[i] = 0.0
            inner[i] = FAR
        } else {
            // Partly covered edge pixels place the outline between pixels
            val d = 0.5 - a
            outer[i] = if (d > 0) d * d else 0.0
            inner[i] = if (d < 0) d * d else 0.0
        }
    }
    squaredDistances(outer, gridWidth, gridHeight)
    squaredDistances(inner, gridWidth, gridHeight)
    val pixels = IntArray(gridWidth * gridHeight) { i ->
        val distance = sqrt(outer[i]) - sqrt(inner[i])
        val alpha = (255 - 255 * (distance / SDF_RADIUS + SDF_CUTOFF)).roundToInt().coerceIn(0, 255)
        Color.argb(alpha, 255, 255, 255)
    }
    return createBitmap(gridWidth, gridHeight).apply { setPixels(pixels, 0, gridWidth, 0, 0, gridWidth, gridHeight) }
}

/** Squared distance of every cell to the nearest zero cell (Felzenszwalb and Huttenlocher). */
private fun squaredDistances(grid: DoubleArray, width: Int, height: Int) {
    val size = max(width, height)
    val f = DoubleArray(size)
    val v = IntArray(size)
    val z = DoubleArray(size + 1)
    for (x in 0 until width) edt1d(grid, x, width, height, f, v, z)
    for (y in 0 until height) edt1d(grid, y * width, 1, width, f, v, z)
}

private fun edt1d(grid: DoubleArray, offset: Int, stride: Int, length: Int, f: DoubleArray, v: IntArray, z: DoubleArray) {
    v[0] = 0
    z[0] = -FAR
    z[1] = FAR
    f[0] = grid[offset]
    var k = 0
    for (q in 1 until length) {
        f[q] = grid[offset + q * stride]
        var s: Double
        do {
            val r = v[k]
            s = (f[q] - f[r] + q.toDouble() * q - r.toDouble() * r) / (q - r) / 2
        } while (s <= z[k] && --k > -1)
        k++
        v[k] = q
        z[k] = s
        z[k + 1] = FAR
    }
    k = 0
    for (q in 0 until length) {
        while (z[k + 1] < q) k++
        val r = v[k]
        val qr = (q - r).toDouble()
        grid[offset + q * stride] = f[r] + qr * qr
    }
}

private const val SDF_RADIUS = 8.0
private const val SDF_CUTOFF = 0.25
private const val SDF_BUFFER = 3
private const val FAR = 1e20

/** The generic aircraft icon is 28 dp, tar1090's airliner 32 px. */
private const val DP_PER_TAR1090_PX = 28f / 32f

/** tar1090 strokes each outline 1.4 wide, half of it outside the fill. */
private const val OUTLINE_WIDTH = 1.4f

private val TAG = logTag("Map", "Native", "ShapeImages")
