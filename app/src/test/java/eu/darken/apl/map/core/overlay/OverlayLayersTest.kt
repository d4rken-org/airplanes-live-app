package eu.darken.apl.map.core.overlay

import eu.darken.apl.server.api.OverlayAttributionDto
import eu.darken.apl.server.api.OverlayCatalogResponse
import eu.darken.apl.server.api.OverlayDto
import eu.darken.apl.server.api.OverlayFrameDto
import eu.darken.apl.server.api.OverlayVectorStyleDto
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class OverlayLayersTest : BaseTest() {

    private fun drawable(
        overlays: List<OverlayDto>,
        enabled: Set<String> = ALL,
        isBlocked: Boolean = false,
        baseUrl: String = BASE_URL,
    ) = OverlayLayers.drawable(overlays, enabled, isBlocked, baseUrl)

    @Test
    fun `the tile template keeps its placeholders, with or without a trailing slash on the base URL`() {
        val expected = "https://api.example.test/api/v1/overlays/radar/$RADAR_FRAME/{z}/{x}/{y}.png"

        drawable(listOf(radar()), baseUrl = "https://api.example.test/").single().tiles shouldBe expected
        drawable(listOf(radar()), baseUrl = "https://api.example.test").single().tiles shouldBe expected
    }

    @Test
    fun `only overlays switched on are drawn, in catalog order`() {
        val catalog = listOf(radar(), restrictions(), military())

        drawable(catalog, enabled = setOf(OverlayDto.ID_MILITARY, OverlayDto.ID_RADAR)).map { it.id } shouldBe
            listOf(OverlayDto.ID_RADAR, OverlayDto.ID_MILITARY)
        drawable(catalog, enabled = emptySet()).shouldBeEmpty()
    }

    @Test
    fun `unavailable, locked, tile-less and unknown overlays are not drawn`() {
        val catalog = listOf(
            radar().copy(status = OverlayDto.STATUS_UNAVAILABLE),
            restrictions().copy(access = OverlayDto.ACCESS_UPGRADE_REQUIRED),
            military().copy(tiles = null),
            radar().copy(id = "lightning", kind = "heatmap"),
        )

        drawable(catalog, enabled = ALL + "lightning").shouldBeEmpty()
    }

    @Test
    fun `nothing is drawn while overlays are blocked`() {
        drawable(listOf(radar(), restrictions(), military()), isBlocked = true).shouldBeEmpty()
    }

    @Test
    fun `a raster overlay keeps its tile size, zoom range and bounds`() {
        val spec = drawable(listOf(radar())).single()

        spec.drawing shouldBe OverlaySpec.Raster(tileSize = 256)
        spec.minZoom shouldBe 0
        spec.maxZoom shouldBe 10
        spec.bounds shouldBe OverlaySpec.Bounds(west = -152.0, south = 20.0, east = 69.0, north = 74.0)
        spec.sourceId shouldBe "apl-overlay-radar"
        spec.layerIds shouldBe listOf("apl-overlay-radar-raster")
    }

    @Test
    fun `a vector overlay with a label property gets fill, line and labels in its fixed colours`() {
        val spec = drawable(listOf(restrictions())).single()

        spec.drawing shouldBe OverlaySpec.Vector(
            sourceLayer = "restrictions",
            lineColor = "#ff0000",
            lineWidth = 2.0,
            fillColor = "#ff0000",
            fillOpacity = 0.08,
            labelProperty = "label",
            colorProperty = null,
        )
        spec.layerIds shouldBe listOf(
            "apl-overlay-restrictions-fill",
            "apl-overlay-restrictions-line",
            "apl-overlay-restrictions-label",
        )
    }

    @Test
    fun `a vector overlay without a label property gets no labels, a colour property colours by feature`() {
        val spec = drawable(listOf(military())).single()

        (spec.drawing as OverlaySpec.Vector).colorProperty shouldBe "color"
        (spec.drawing as OverlaySpec.Vector).labelProperty shouldBe null
        spec.layerIds shouldBe listOf("apl-overlay-military-fill", "apl-overlay-military-line")
    }

    @Test
    fun `attribution is a link per source, escaped`() {
        val overlay = radar().copy(
            attribution = listOf(
                OverlayAttributionDto("EUMETNET OPERA (CC BY 4.0)", "https://example.test/opera"),
                OverlayAttributionDto("Rock & <Roll> \"Radar\"", "https://example.test/?a=1&b=\"2\""),
            ),
        )

        drawable(listOf(overlay)).single().attribution shouldBe
            "<a href=\"https://example.test/opera\">EUMETNET OPERA (CC BY 4.0)</a> " +
            "<a href=\"https://example.test/?a=1&amp;b=&quot;2&quot;\">Rock &amp; &lt;Roll&gt; &quot;Radar&quot;</a>"
        drawable(listOf(radar().copy(attribution = emptyList()))).single().attribution shouldBe ""
    }

    @Test
    fun `a new frame changes the spec, reading the same catalog again does not`() {
        val first = OverlayCatalogResponse(generatedAt = 1_794_225_640_000L, overlays = listOf(radar(), restrictions()))
        val again = OverlayCatalogResponse(
            generatedAt = 1_794_225_940_000L,
            overlays = listOf(radar().copy(coverage = listOf("Europe", "Poland")), restrictions()),
        )
        val next = OverlayCatalogResponse(
            generatedAt = 1_794_225_940_000L,
            overlays = listOf(radar(frame = "7a1d02c3e9b8"), restrictions()),
        )

        drawable(again.overlays) shouldBe drawable(first.overlays)
        drawable(next.overlays)[0] shouldNotBe drawable(first.overlays)[0]
        drawable(next.overlays)[1] shouldBe drawable(first.overlays)[1]
    }

    companion object {
        const val BASE_URL = "https://api.example.test/"
        const val RADAR_FRAME = "3f9c2a1b7d40"
        val ALL = setOf(OverlayDto.ID_RADAR, OverlayDto.ID_RESTRICTIONS, OverlayDto.ID_MILITARY)

        fun radar(frame: String = RADAR_FRAME) = OverlayDto(
            id = OverlayDto.ID_RADAR,
            kind = OverlayDto.KIND_RASTER,
            category = OverlayDto.CATEGORY_WEATHER,
            coverage = listOf("Europe"),
            title = "Rain radar",
            forecast = false,
            attribution = listOf(OverlayAttributionDto("EUMETNET OPERA (CC BY 4.0)", "https://example.test/opera")),
            status = OverlayDto.STATUS_AVAILABLE,
            access = OverlayDto.ACCESS_ALLOWED,
            tiles = "/api/v1/overlays/radar/$frame/{z}/{x}/{y}.png",
            minZoom = 0,
            maxZoom = 10,
            tileSize = 256,
            bounds = listOf(-152.0, 20.0, 69.0, 74.0),
            frame = OverlayFrameDto(id = frame, time = 1_794_225_300_000L, parts = emptyList()),
            refreshSeconds = 300,
        )

        fun restrictions(frame: String = "a41c09e2b6f3") = OverlayDto(
            id = OverlayDto.ID_RESTRICTIONS,
            kind = OverlayDto.KIND_VECTOR,
            category = OverlayDto.CATEGORY_AIRSPACE,
            coverage = listOf("United States"),
            title = "Flight restrictions",
            forecast = false,
            attribution = listOf(OverlayAttributionDto("FAA via tar1090-aux", "https://example.test/aux")),
            status = OverlayDto.STATUS_AVAILABLE,
            access = OverlayDto.ACCESS_ALLOWED,
            tiles = "/api/v1/overlays/restrictions/$frame/{z}/{x}/{y}.mvt",
            minZoom = 0,
            maxZoom = 12,
            tileSize = 512,
            bounds = listOf(-158.1, 19.6, -67.2, 61.3),
            frame = OverlayFrameDto(id = frame, time = 1_794_204_000_000L, parts = emptyList()),
            refreshSeconds = 300,
            sourceLayer = "restrictions",
            style = OverlayVectorStyleDto(
                lineColor = "#ff0000",
                lineWidth = 2.0,
                fillColor = "#ff0000",
                fillOpacity = 0.08,
                labelProperty = "label",
            ),
        )

        fun military(frame: String = "c0ffee123456") = OverlayDto(
            id = OverlayDto.ID_MILITARY,
            kind = OverlayDto.KIND_VECTOR,
            category = OverlayDto.CATEGORY_AIRSPACE,
            coverage = listOf("United States", "Germany"),
            title = "Military airspace",
            forecast = false,
            attribution = emptyList(),
            status = OverlayDto.STATUS_AVAILABLE,
            access = OverlayDto.ACCESS_ALLOWED,
            tiles = "/api/v1/overlays/military/$frame/{z}/{x}/{y}.mvt",
            minZoom = 0,
            maxZoom = 12,
            tileSize = 512,
            bounds = listOf(-170.0, 15.0, 30.0, 70.0),
            frame = OverlayFrameDto(id = frame, time = 1_794_204_000_000L, parts = emptyList()),
            refreshSeconds = 3600,
            sourceLayer = "military",
            style = OverlayVectorStyleDto(
                lineColor = "#4895ef",
                lineWidth = 1.5,
                fillColor = "#4895ef",
                fillOpacity = 0.1,
                colorProperty = "color",
            ),
        )
    }
}
