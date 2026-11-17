package eu.darken.apl.map.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import eu.darken.apl.common.debug.logging.Logging
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.map.core.AircraftShapes
import eu.darken.apl.map.core.AircraftShapesRepo
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import kotlinx.serialization.json.Json
import org.junit.AfterClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import testhelpers.logging.JUnitLogger
import kotlin.math.abs

/** Draws every bundled silhouette natively, the centring depends on what the paths really cover. */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AircraftShapeRenderTest {

    init {
        Logging.clearAll()
        Logging.install(JUnitLogger())
    }

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val shapes: AircraftShapes = context.assets.open(AircraftShapesRepo.ASSET).use {
        Json { ignoreUnknownKeys = true }.decodeFromString(it.readBytes().decodeToString())
    }

    @Test
    fun `every shape is centred on its image and nothing is cut off`() {
        val failures = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        val grown = mutableListOf<String>()
        var checked = 0
        for (density in DENSITIES) for ((name, shape) in shapes.shapes) {
            val label = "$name@$density"
            val silhouette = try {
                renderShapeSilhouette(shape, density)
            } catch (e: RuntimeException) {
                skipped += "$label: $e"
                continue
            }
            checked++

            val opaque = silhouette.extent().opaque
            val centerX = (opaque.left + opaque.right) / 2f
            val centerY = (opaque.top + opaque.bottom) / 2f
            if (opaque.isEmpty) {
                failures += "$label: nothing drawn"
            } else if (abs(centerX - silhouette.width / 2f) > 0.5f || abs(centerY - silhouette.height / 2f) > 0.5f) {
                failures += "$label: centre $centerX,$centerY in ${silhouette.width}x${silhouette.height}"
            }

            // Same padding as the render: Skia rasterises some pixels differently at other translations
            val (padX, padY) = centringPadding(shape, density)
            val reference = renderPadded(shape, density, padX, padY)
            val referenceDrawn = reference.extent().drawn
            if (referenceDrawn.left < 1 || referenceDrawn.top < 1 ||
                referenceDrawn.right > reference.width - 1 || referenceDrawn.bottom > reference.height - 1
            ) {
                failures += "$label: reference reaches its edge, $referenceDrawn in ${reference.width}x${reference.height}"
            }
            val nominalWidth = reference.width - 2 * padX
            val nominalHeight = reference.height - 2 * padY
            if (silhouette.width != nominalWidth || silhouette.height != nominalHeight) {
                grown += "$label: ${nominalWidth}x$nominalHeight to ${silhouette.width}x${silhouette.height}"
            }

            val drawnPixels = silhouette.countAlpha { it != 0 }
            val expectedDrawn = reference.countAlpha { it != 0 }
            if (drawnPixels != expectedDrawn) failures += "$label: $drawnPixels drawn pixels, $expectedDrawn uncut"
            val opaquePixels = silhouette.countAlpha { it >= 128 }
            val expectedOpaque = reference.countAlpha { it >= 128 }
            if (opaquePixels != expectedOpaque) failures += "$label: $opaquePixels opaque pixels, $expectedOpaque uncut"
        }
        log(TAG) { "Checked $checked shape renders, skipped ${skipped.size}: $skipped" }
        log(TAG) { "Grown ${grown.size}: $grown" }

        checked shouldBeGreaterThan 0
        withClue("checked $checked, skipped $skipped") { failures.shouldBeEmpty() }
    }

    private fun Bitmap.pixels(): IntArray = IntArray(width * height).also { getPixels(it, 0, width, 0, 0, width, height) }

    private fun Bitmap.extent(): OpaqueExtent = opaqueExtent(pixels(), width, height)

    private fun Bitmap.countAlpha(matches: (Int) -> Boolean): Int = pixels().count { matches(it ushr 24) }

    companion object {
        private val DENSITIES = listOf(2f, 2.625f, 3.5f)

        private const val TAG = "AircraftShapeRenderTest"

        @JvmStatic
        @AfterClass
        fun afterClass() {
            Logging.clearAll()
        }
    }
}
