package eu.darken.apl.watch.ui.thumbnail

import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class LocationThumbnailGeometryTest : BaseTest() {

    private fun query(
        latitude: Double = 50.8,
        longitude: Double = 4.35,
        radiusInMeters: Float = 25_000f,
        spec: LocationThumbnailSpec = LocationThumbnailSpec.LIST,
    ) = spec.query(
        latitude = latitude,
        longitude = longitude,
        radiusInMeters = radiusInMeters,
        density = 2.625f,
        darkTheme = false,
        circleColor = 0xFF3366CC.toInt(),
    )

    @Test
    fun `list spec keeps the circle inside every crop`() {
        LocationThumbnailSpec.LIST.circleDiameterDp shouldBe (62.4f plusOrMinus 0.01f)
    }

    @Test
    fun `header spec renders 3 to 2 and keeps the circle inside every crop`() {
        val spec = LocationThumbnailSpec.header(360)
        spec.widthDp shouldBe 360
        spec.heightDp shouldBe 240
        // 0.9 * min(360, 240, 240 * 0.6, 360 / 1.5)
        spec.circleDiameterDp shouldBe (129.6f plusOrMinus 0.01f)
    }

    @Test
    fun `crop safe diameter uses the tightest crop`() {
        LocationThumbnailGeometry.cropSafeDiameter(100f, 100f, 1f, 1f) shouldBe (90f plusOrMinus 0.001f)
        LocationThumbnailGeometry.cropSafeDiameter(200f, 100f, 0.5f, 2f) shouldBe (45f plusOrMinus 0.001f)
    }

    @Test
    fun `camera zoom is clamped`() {
        LocationThumbnailGeometry.camera(query(latitude = 0.0, radiusInMeters = 1f)).zoom shouldBe
            LocationThumbnailGeometry.MAX_ZOOM
        LocationThumbnailGeometry.camera(query(latitude = 0.0, radiusInMeters = 5_000_000f)).zoom shouldBe
            LocationThumbnailGeometry.MIN_ZOOM
    }

    @Test
    fun `areas beyond the web mercator limit are not renderable`() {
        LocationThumbnailGeometry.isRenderable(query(latitude = 85.0)) shouldBe false
        LocationThumbnailGeometry.isRenderable(query(latitude = -85.0)) shouldBe false
        LocationThumbnailGeometry.isRenderable(query(latitude = 89.0)) shouldBe false
        LocationThumbnailGeometry.isRenderable(query(latitude = -89.0)) shouldBe false
        LocationThumbnailGeometry.isRenderable(query(latitude = 83.0, radiusInMeters = 250_000f)) shouldBe false
        LocationThumbnailGeometry.isRenderable(query(latitude = -83.0, radiusInMeters = 250_000f)) shouldBe false
    }

    @Test
    fun `areas within the web mercator limit are renderable`() {
        LocationThumbnailGeometry.isRenderable(query(latitude = 80.0)) shouldBe true
        LocationThumbnailGeometry.isRenderable(query(latitude = -80.0)) shouldBe true
    }

    @Test
    fun `areas on the antimeridian are renderable and keep their longitude`() {
        for (longitude in listOf(180.0, -180.0)) {
            val query = query(latitude = 0.0, longitude = longitude)
            LocationThumbnailGeometry.isRenderable(query) shouldBe true
            LocationThumbnailGeometry.camera(query).longitude shouldBe longitude
        }
    }

    @Test
    fun `cache key covers every field`() {
        val base = query()
        val variants = listOf(
            base.copy(latitude = 50.9),
            base.copy(longitude = 4.4),
            base.copy(radiusInMeters = 26_000f),
            base.copy(widthDp = 105),
            base.copy(heightDp = 209),
            base.copy(density = 3f),
            base.copy(circleDiameterDp = 60f),
            base.copy(darkTheme = true),
            base.copy(circleColor = 0xFF000000.toInt()),
        )
        val keys = (variants + base).map { it.cacheKey }
        keys.toSet().size shouldBe keys.size
        keys.all { it.startsWith("location-thumb-v2-") } shouldBe true
    }
}
