package eu.darken.apl.watch.ui.thumbnail

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class MapPointThumbnailQueryTest : BaseTest() {

    private fun query(
        latitude: Double = 50.033333,
        longitude: Double = 8.570556,
        zoom: Double = 12.0,
        spec: MapPointThumbnailSpec = MapPointThumbnailSpec.header(360),
        darkTheme: Boolean = false,
    ) = spec.query(
        latitude = latitude,
        longitude = longitude,
        zoom = zoom,
        density = 2.625f,
        darkTheme = darkTheme,
    )

    @Test
    fun `header spec renders 3 to 2 at twice the layout size`() {
        val spec = MapPointThumbnailSpec.header(360)
        spec.widthDp shouldBe 360
        spec.heightDp shouldBe 240
        spec.renderScale shouldBe 2f
    }

    @Test
    fun `cache key has its own prefix`() {
        val key = query().cacheKey
        key shouldStartWith MapPointThumbnailQuery.KEY_PREFIX
        key shouldStartWith "map-point-thumb-v1-"
        MapPointThumbnailQuery.KEY_PREFIX shouldNotBe LocationThumbnailQuery.KEY_PREFIX
    }

    @Test
    fun `cache key includes the zoom`() {
        query(zoom = 13.5).cacheKey shouldContain "-z13.5"
    }

    @Test
    fun `cache key is stable for equal queries`() {
        query().cacheKey shouldBe query().cacheKey
    }

    @Test
    fun `cache key changes with zoom, size and theme`() {
        val base = query().cacheKey
        query(zoom = 13.0).cacheKey shouldNotBe base
        query(spec = MapPointThumbnailSpec.header(300)).cacheKey shouldNotBe base
        query(darkTheme = true).cacheKey shouldNotBe base
    }

    @Test
    fun `points up to the web mercator limit are renderable`() {
        LocationThumbnailGeometry.isRenderable(query(latitude = 85.05)) shouldBe true
        LocationThumbnailGeometry.isRenderable(query(latitude = -85.05)) shouldBe true
        LocationThumbnailGeometry.isRenderable(query(latitude = 0.0)) shouldBe true
    }

    @Test
    fun `points beyond the web mercator limit are not renderable`() {
        LocationThumbnailGeometry.isRenderable(query(latitude = 85.06)) shouldBe false
        LocationThumbnailGeometry.isRenderable(query(latitude = -85.06)) shouldBe false
        LocationThumbnailGeometry.isRenderable(query(latitude = 89.0)) shouldBe false
    }

    @Test
    fun `points without a size are not renderable`() {
        LocationThumbnailGeometry.isRenderable(query().copy(widthDp = 0)) shouldBe false
        LocationThumbnailGeometry.isRenderable(query().copy(heightDp = 0)) shouldBe false
    }

    @Test
    fun `point camera centres on the point at its zoom, clamped`() {
        LocationThumbnailGeometry.camera(query(zoom = 13.0)) shouldBe LocationThumbnailCamera(
            latitude = 50.033333,
            longitude = 8.570556,
            zoom = 13.0,
        )
        LocationThumbnailGeometry.camera(query(zoom = 25.0)).zoom shouldBe LocationThumbnailGeometry.MAX_ZOOM
        LocationThumbnailGeometry.camera(query(zoom = -1.0)).zoom shouldBe LocationThumbnailGeometry.MIN_ZOOM
    }
}
