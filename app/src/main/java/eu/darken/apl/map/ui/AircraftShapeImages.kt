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
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal fun shapeImageName(shape: String) = "apl-shape-$shape"

/** Distance-field images by image name, and how far each silhouette reaches from its image centre in dp. */
internal data class ShapeImages(val images: Map<String, Bitmap>, val farthestDp: Map<String, Float>)

/**
 * White silhouettes as distance fields the map tints by altitude. The fill carries tar1090's
 * outline width, which keeps thin wings visible and the shapes as large as on the website.
 */
internal fun renderShapeImages(shapes: AircraftShapes, density: Float): ShapeImages {
    val images = HashMap<String, Bitmap>()
    val farthest = HashMap<String, Float>()
    shapes.shapes.forEach { (name, shape) ->
        try {
            val silhouette = render(shape, density)
            val imageName = shapeImageName(name)
            farthest[imageName] = silhouette.farthestOpaqueDp(density)
            images[imageName] = silhouette.toDistanceField()
        } catch (e: RuntimeException) {
            log(TAG) { "Shape $name could not be drawn: $e" }
        }
    }
    return ShapeImages(images, farthest)
}

/** The same white silhouette as [renderShapeImages] draws, without turning it into a distance field. */
internal fun renderShapeSilhouette(shape: AircraftShapes.Shape, density: Float): Bitmap = render(shape, density)

/** How far the bitmap's farthest opaque pixel reaches from its centre, in dp at [density]. */
internal fun Bitmap.farthestOpaqueDp(density: Float): Float {
    val pixels = IntArray(width * height).also { getPixels(it, 0, width, 0, 0, width, height) }
    return opaqueExtent(pixels, width, height).farthest / density
}

/**
 * Drawn into a padded bitmap first and then moved by whole pixels so the drawn shape sits on the
 * image centre: a viewBox doesn't centre its shape, and the selection halo is centred on the image.
 */
private fun render(shape: AircraftShapes.Shape, density: Float): Bitmap {
    val width = nominalSize(shape.w, density)
    val height = nominalSize(shape.h, density)
    val padX = (width + 1) / 2
    val padY = (height + 1) / 2
    val padded = renderPadded(shape, density, padX, padY)
    val paddedWidth = padded.width
    val paddedHeight = padded.height

    val pixels = IntArray(paddedWidth * paddedHeight)
    padded.getPixels(pixels, 0, paddedWidth, 0, 0, paddedWidth, paddedHeight)
    val extent = opaqueExtent(pixels, paddedWidth, paddedHeight)
    val touchesEdge = !extent.isEmpty &&
        (extent.left == 0 || extent.top == 0 || extent.right == paddedWidth || extent.bottom == paddedHeight)
    if (extent.isEmpty || touchesEdge) {
        if (touchesEdge) log(TAG) { "Shape reaches its padding, drawn uncentred: $extent in ${paddedWidth}x$paddedHeight" }
        return Bitmap.createBitmap(padded, padX, padY, width, height)
    }

    val placement = centredPlacement(
        left = extent.left - padX,
        top = extent.top - padY,
        right = extent.right - padX,
        bottom = extent.bottom - padY,
        width = width,
        height = height,
    )
    // Copied pixel for pixel, resampling would soften the outline
    val shiftX = placement.offsetX - padX
    val shiftY = placement.offsetY - padY
    val placed = IntArray(placement.width * placement.height)
    for (y in 0 until placement.height) {
        val sourceY = y - shiftY
        if (sourceY !in 0 until paddedHeight) continue
        for (x in 0 until placement.width) {
            val sourceX = x - shiftX
            if (sourceX !in 0 until paddedWidth) continue
            placed[y * placement.width + x] = pixels[sourceY * paddedWidth + sourceX]
        }
    }
    return createBitmap(placement.width, placement.height).apply {
        setPixels(placed, 0, placement.width, 0, 0, placement.width, placement.height)
    }
}

private fun nominalSize(tar1090Px: Float, density: Float): Int =
    ceil(tar1090Px * DP_PER_TAR1090_PX * density).toInt().coerceAtLeast(1)

/** The shape where its viewBox places it, with [padX] and [padY] empty pixels around its nominal size. */
internal fun renderPadded(shape: AircraftShapes.Shape, density: Float, padX: Int, padY: Int): Bitmap {
    val width = nominalSize(shape.w, density)
    val height = nominalSize(shape.h, density)
    val (minX, minY, boxWidth, boxHeight) = shape.viewBox
    var scaleX = width / boxWidth
    var scaleY = height / boxHeight
    if (!shape.stretch) {
        scaleX = min(scaleX, scaleY)
        scaleY = scaleX
    }

    val bitmap = createBitmap(width + 2 * padX, height + 2 * padY)
    val canvas = Canvas(bitmap)
    canvas.translate(padX.toFloat(), padY.toFloat())
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

/** Where a drawing goes in its image:[offsetX] and [offsetY] are added to its pixel coordinates. */
internal data class Placement(val offsetX: Int, val offsetY: Int, val width: Int, val height: Int)

/**
 * Moves the box from [left], [top] to [right], [bottom] (exclusive) by whole pixels so its centre lands
 * within half a pixel of the centre of a [width] by [height] image. The image grows by the same amount
 * on opposite sides, only when the moved box would not fit.
 */
internal fun centredPlacement(left: Int, top: Int, right: Int, bottom: Int, width: Int, height: Int): Placement {
    val (offsetX, placedWidth) = centredAxis(left, right, width)
    val (offsetY, placedHeight) = centredAxis(top, bottom, height)
    return Placement(offsetX, offsetY, placedWidth, placedHeight)
}

private fun centredAxis(start: Int, end: Int, size: Int): Pair<Int, Int> {
    var grown = size
    while (true) {
        // round((grown - start - end) / 2), halves up
        val offset = Math.floorDiv(grown - start - end + 1, 2)
        if (start + offset >= 0 && end + offset <= grown) return offset to grown
        grown += 2
    }
}

/** The box around pixels with at least half alpha, right and bottom exclusive; all zero when there are none. */
internal data class OpaqueExtent(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    /** From the image centre to the farthest corner of any such pixel, in pixels. */
    val farthest: Float,
) {
    val isEmpty: Boolean get() = right <= left || bottom <= top
}

/** Scans the ARGB [pixels], row by row, of a [width] by [height] image. */
internal fun opaqueExtent(pixels: IntArray, width: Int, height: Int): OpaqueExtent {
    var left = width
    var top = height
    var right = 0
    var bottom = 0
    var farthestSquared = 0f
    val centerX = width / 2f
    val centerY = height / 2f
    for (y in 0 until height) {
        val dy = max(abs(y - centerY), abs(y + 1 - centerY))
        for (x in 0 until width) {
            if (pixels[y * width + x] ushr 24 < OPAQUE_ALPHA) continue
            if (x < left) left = x
            if (x >= right) right = x + 1
            if (y < top) top = y
            if (y >= bottom) bottom = y + 1
            val dx = max(abs(x - centerX), abs(x + 1 - centerX))
            farthestSquared = max(farthestSquared, dx * dx + dy * dy)
        }
    }
    if (right <= left) return OpaqueExtent(0, 0, 0, 0, 0f)
    return OpaqueExtent(left, top, right, bottom, sqrt(farthestSquared))
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

private const val OPAQUE_ALPHA = 128

/** The generic aircraft icon is 28 dp, tar1090's airliner 32 px. */
private const val DP_PER_TAR1090_PX = 28f / 32f

/** tar1090 strokes each outline 1.4 wide, half of it outside the fill. */
private const val OUTLINE_WIDTH = 1.4f

private val TAG = logTag("Map", "Native", "ShapeImages")
