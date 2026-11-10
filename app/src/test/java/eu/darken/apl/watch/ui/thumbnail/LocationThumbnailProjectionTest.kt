package eu.darken.apl.watch.ui.thumbnail

import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan

class LocationThumbnailProjectionTest : BaseTest() {

    private fun mercatorY(latitude: Double): Double = ln(tan(PI / 4 + Math.toRadians(latitude) / 2)) / (2 * PI)

    /** Distance in logical px from the center to the circle's north edge, as Web Mercator draws it at the camera's zoom. */
    private fun projectedRadiusPx(query: LocationThumbnailQuery): Double {
        val zoom = LocationThumbnailGeometry.camera(query).zoom
        val north = query.latitude + LocationThumbnailGeometry.latitudeSpan(query.radiusInMeters)
        return 512.0 * 2.0.pow(zoom) * abs(mercatorY(north) - mercatorY(query.latitude))
    }

    @Test
    fun `the projected circle matches the planned diameter at high latitudes`() {
        for (spec in listOf(LocationThumbnailSpec.LIST, LocationThumbnailSpec.header(360))) {
            for (latitude in listOf(0.0, 50.8, 70.0, 80.0, -80.0)) {
                for (radius in listOf(2_000f, 25_000f, 250_000f)) {
                    val query = spec.query(
                        latitude = latitude,
                        longitude = 0.0,
                        radiusInMeters = radius,
                        density = 2.625f,
                        darkTheme = false,
                        circleColor = 0xFF3366CC.toInt(),
                    )
                    val expected = spec.circleDiameterDp / 2.0
                    projectedRadiusPx(query) shouldBe (expected plusOrMinus expected * 0.01)
                }
            }
        }
    }
}
