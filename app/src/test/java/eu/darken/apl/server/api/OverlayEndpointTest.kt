package eu.darken.apl.server.api

import eu.darken.apl.common.BuildConfigWrap
import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.common.http.HttpModule
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.ServerModule
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import testhelper.TestCompatibilityStore
import testhelper.coroutine.TestDispatcherProvider

class OverlayEndpointTest : BaseTest() {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var endpoint: ServerEndpoint
    private val compatStore = TestCompatibilityStore()

    private val fakeMonotonicClock = object : MonotonicClock {
        override fun elapsed(): Long = 1_000L
    }

    @BeforeEach
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        endpoint = ServerEndpoint(
            baseClient = HttpModule().baseHttpClient(),
            json = ServerModule.serverJson(),
            dispatcherProvider = TestDispatcherProvider(),
            serverClock = ServerClock(fakeMonotonicClock),
            compatibility = compatStore.holder(),
        ).apply {
            baseUrl = mockWebServer.url("/").toString()
        }
    }

    @AfterEach
    fun teardown() {
        mockWebServer.shutdown()
        compatStore.close()
    }

    @Test
    fun `the feeder catalog is read with the token and decodes every overlay`() = runTest {
        mockWebServer.enqueue(MockResponse().setBody(FEEDER_CATALOG))

        val response = endpoint.overlays("token-value")

        val request = mockWebServer.takeRequest()
        request.method shouldBe "GET"
        request.path shouldBe "/api/v1/overlays"
        request.getHeader("Authorization") shouldBe "Bearer token-value"
        request.getHeader("X-App-Version") shouldBe BuildConfigWrap.VERSION_CODE.toString()
        request.getHeader("X-App-Variant") shouldBe ServerVariant.CURRENT

        response shouldBe OverlayCatalogResponse(
            generatedAt = 1_794_225_640_000L,
            overlays = listOf(
                OverlayDto(
                    id = OverlayDto.ID_RADAR,
                    kind = OverlayDto.KIND_RASTER,
                    category = OverlayDto.CATEGORY_WEATHER,
                    coverage = listOf("United States", "Canada", "Europe"),
                    title = "Rain radar",
                    forecast = false,
                    attribution = listOf(
                        OverlayAttributionDto(
                            text = "Data Source: Environment and Climate Change Canada",
                            url = "https://eccc-msc.github.io/open-data/licence/readme_en/",
                        ),
                        OverlayAttributionDto(
                            text = "EUMETNET OPERA (CC BY 4.0)",
                            url = "https://www.eumetnet.eu/activities/observations-programme/current-activities/opera/",
                        ),
                    ),
                    status = OverlayDto.STATUS_AVAILABLE,
                    access = OverlayDto.ACCESS_ALLOWED,
                    tiles = "/api/v1/overlays/radar/3f9c2a1b7d40/{z}/{x}/{y}.png",
                    minZoom = 0,
                    maxZoom = 10,
                    tileSize = 256,
                    bounds = listOf(-152.0, 20.0, 69.0, 74.0),
                    frame = OverlayFrameDto(
                        id = "3f9c2a1b7d40",
                        time = 1_794_225_300_000L,
                        parts = listOf(
                            OverlayFramePartDto(
                                id = "eccc-radar",
                                coverage = listOf("United States", "Canada"),
                                time = 1_794_225_600_000L,
                            ),
                            OverlayFramePartDto(
                                id = "opera-radar",
                                coverage = listOf("Europe"),
                                time = 1_794_225_300_000L,
                            ),
                        ),
                    ),
                    refreshSeconds = 300,
                ),
                OverlayDto(
                    id = OverlayDto.ID_RESTRICTIONS,
                    kind = OverlayDto.KIND_VECTOR,
                    category = OverlayDto.CATEGORY_AIRSPACE,
                    coverage = listOf("United States"),
                    title = "Flight restrictions",
                    forecast = false,
                    attribution = listOf(
                        OverlayAttributionDto(
                            text = "FAA via tar1090-aux",
                            url = "https://github.com/wiedehopf/tar1090-aux",
                        ),
                    ),
                    status = OverlayDto.STATUS_AVAILABLE,
                    access = OverlayDto.ACCESS_ALLOWED,
                    tiles = "/api/v1/overlays/restrictions/a41c09e2b6f3/{z}/{x}/{y}.mvt",
                    minZoom = 0,
                    maxZoom = 12,
                    tileSize = 512,
                    bounds = listOf(-158.1, 19.6, -67.2, 61.3),
                    frame = OverlayFrameDto(
                        id = "a41c09e2b6f3",
                        time = 1_794_204_000_000L,
                        parts = listOf(
                            OverlayFramePartDto(
                                id = "faa-tfr",
                                coverage = listOf("United States"),
                                time = 1_794_204_000_000L,
                            ),
                        ),
                    ),
                    refreshSeconds = 300,
                    sourceLayer = "restrictions",
                    style = OverlayVectorStyleDto(
                        lineColor = "#ff0000",
                        lineWidth = 2.0,
                        fillColor = "#ff0000",
                        fillOpacity = 0.08,
                        labelProperty = "label",
                    ),
                ),
                OverlayDto(
                    id = OverlayDto.ID_MILITARY,
                    kind = OverlayDto.KIND_VECTOR,
                    category = OverlayDto.CATEGORY_AIRSPACE,
                    coverage = listOf("United States", "United Kingdom"),
                    title = "Military airspace",
                    forecast = false,
                    attribution = listOf(
                        OverlayAttributionDto(text = "tar1090", url = "https://github.com/wiedehopf/tar1090"),
                    ),
                    status = OverlayDto.STATUS_AVAILABLE,
                    access = OverlayDto.ACCESS_ALLOWED,
                    tiles = "/api/v1/overlays/military/77d1e0c4a9b2/{z}/{x}/{y}.mvt",
                    minZoom = 0,
                    maxZoom = 12,
                    tileSize = 512,
                    bounds = listOf(-170.0, 18.0, 10.0, 72.0),
                    frame = OverlayFrameDto(
                        id = "77d1e0c4a9b2",
                        time = 1_794_200_000_000L,
                        parts = listOf(
                            OverlayFramePartDto(
                                id = "faa-sua",
                                coverage = listOf("United States"),
                                time = 1_794_200_000_000L,
                            ),
                        ),
                    ),
                    refreshSeconds = 3600,
                    sourceLayer = "military",
                    style = OverlayVectorStyleDto(
                        lineColor = "#4895ef",
                        lineWidth = 1.5,
                        fillColor = "#4895ef",
                        fillOpacity = 0.1,
                        colorProperty = "color",
                    ),
                ),
            ),
        )
        response.overlays.map { it.isRaster } shouldBe listOf(true, false, false)
        response.overlays.map { it.isVector } shouldBe listOf(false, true, true)
        response.overlays.all { it.isAvailable && it.isAllowed } shouldBe true
        response.overlays[0].sourceLayer.shouldBeNull()
        response.overlays[0].style.shouldBeNull()
        response.overlays[1].style!!.colorProperty.shouldBeNull()
        response.overlays[2].style!!.labelProperty.shouldBeNull()
    }

    @Test
    fun `a locked overlay decodes without tiles or frame but keeps its attribution`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setBody(
                """
                {
                  "generatedAt": 1794225640000,
                  "overlays": [
                    {
                      "id": "radar", "kind": "raster", "category": "weather",
                      "coverage": ["United States", "Canada", "Europe"],
                      "title": "Rain radar", "forecast": false,
                      "attribution": [{ "text": "EUMETNET OPERA (CC BY 4.0)", "url": "https://www.eumetnet.eu/" }],
                      "status": "available", "access": "upgrade_required",
                      "minZoom": 0, "maxZoom": 10, "tileSize": 256,
                      "bounds": [-152.0, 20.0, 69.0, 74.0],
                      "refreshSeconds": 300
                    }
                  ]
                }
                """.trimIndent()
            )
        )

        val overlay = endpoint.overlays("token").overlays.single()

        overlay.tiles.shouldBeNull()
        overlay.frame.shouldBeNull()
        overlay.isAvailable shouldBe true
        overlay.isAllowed shouldBe false
        overlay.isUpgradeRequired shouldBe true
        overlay.attribution shouldBe listOf(
            OverlayAttributionDto(text = "EUMETNET OPERA (CC BY 4.0)", url = "https://www.eumetnet.eu/"),
        )
    }

    @Test
    fun `an unavailable overlay decodes without bounds, tiles or frame`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setBody(
                """
                {
                  "generatedAt": 1794225640000,
                  "overlays": [
                    {
                      "id": "restrictions", "kind": "vector", "category": "airspace",
                      "coverage": [],
                      "title": "Flight restrictions", "forecast": false,
                      "attribution": [{ "text": "FAA via tar1090-aux", "url": "https://github.com/wiedehopf/tar1090-aux" }],
                      "status": "unavailable", "access": "allowed",
                      "minZoom": 0, "maxZoom": 12, "tileSize": 512,
                      "refreshSeconds": 300,
                      "sourceLayer": "restrictions",
                      "style": { "lineColor": "#ff0000", "lineWidth": 2.0, "fillColor": "#ff0000", "fillOpacity": 0.08,
                                 "labelProperty": "label" }
                    }
                  ]
                }
                """.trimIndent()
            )
        )

        val overlay = endpoint.overlays("token").overlays.single()

        overlay.isAvailable shouldBe false
        overlay.isAllowed shouldBe true
        overlay.coverage.shouldBeEmpty()
        overlay.bounds.shouldBeNull()
        overlay.tiles.shouldBeNull()
        overlay.frame.shouldBeNull()
    }

    @Test
    fun `unknown values and unknown fields decode`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setBody(
                """
                {
                  "generatedAt": 1794225640000,
                  "futureField": {"nested": true},
                  "overlays": [
                    {
                      "id": "lightning", "kind": "heatmap", "category": "hazard",
                      "coverage": ["Europe"],
                      "title": "Lightning", "forecast": true,
                      "attribution": [],
                      "status": "degraded", "access": "trial",
                      "tiles": "/api/v1/overlays/lightning/0a1b/{z}/{x}/{y}.bin",
                      "minZoom": 0, "maxZoom": 8, "tileSize": 256,
                      "bounds": [-30.0, 30.0, 45.0, 72.0],
                      "frame": { "id": "0a1b", "time": 1794225000000, "parts": [], "sequence": 4 },
                      "refreshSeconds": 60,
                      "opacity": 0.5
                    }
                  ]
                }
                """.trimIndent()
            )
        )

        val overlay = endpoint.overlays("token").overlays.single()

        overlay.id shouldBe "lightning"
        overlay.isRaster shouldBe false
        overlay.isVector shouldBe false
        overlay.isAvailable shouldBe false
        overlay.isAllowed shouldBe false
        overlay.isUpgradeRequired shouldBe false
    }

    @Test
    fun `a throttled catalog read surfaces its code and retry delay`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setHeader("Content-Type", "application/problem+json")
                .setHeader("Retry-After", "12")
                .setBody("""{"type": "about:blank", "title": "Too Many Requests", "status": 429, "code": "quota_exceeded", "retryAfterSeconds": 12}""")
        )

        val error = shouldThrow<ServerApiException> { endpoint.overlays("token") }

        error.status shouldBe 429
        error.code shouldBe ServerCodes.QUOTA_EXCEEDED
        error.retryAfterSeconds shouldBe 12L
    }

    companion object {
        private val FEEDER_CATALOG = """
            {
              "generatedAt": 1794225640000,
              "overlays": [
                {
                  "id": "radar", "kind": "raster", "category": "weather",
                  "coverage": ["United States", "Canada", "Europe"],
                  "title": "Rain radar", "forecast": false,
                  "attribution": [
                    { "text": "Data Source: Environment and Climate Change Canada",
                      "url": "https://eccc-msc.github.io/open-data/licence/readme_en/" },
                    { "text": "EUMETNET OPERA (CC BY 4.0)",
                      "url": "https://www.eumetnet.eu/activities/observations-programme/current-activities/opera/" }
                  ],
                  "status": "available", "access": "allowed",
                  "tiles": "/api/v1/overlays/radar/3f9c2a1b7d40/{z}/{x}/{y}.png",
                  "minZoom": 0, "maxZoom": 10, "tileSize": 256,
                  "bounds": [-152.0, 20.0, 69.0, 74.0],
                  "frame": {
                    "id": "3f9c2a1b7d40", "time": 1794225300000,
                    "parts": [
                      { "id": "eccc-radar", "coverage": ["United States", "Canada"], "time": 1794225600000 },
                      { "id": "opera-radar", "coverage": ["Europe"], "time": 1794225300000 }
                    ]
                  },
                  "refreshSeconds": 300
                },
                {
                  "id": "restrictions", "kind": "vector", "category": "airspace",
                  "coverage": ["United States"],
                  "title": "Flight restrictions", "forecast": false,
                  "attribution": [{ "text": "FAA via tar1090-aux", "url": "https://github.com/wiedehopf/tar1090-aux" }],
                  "status": "available", "access": "allowed",
                  "tiles": "/api/v1/overlays/restrictions/a41c09e2b6f3/{z}/{x}/{y}.mvt",
                  "minZoom": 0, "maxZoom": 12, "tileSize": 512,
                  "bounds": [-158.1, 19.6, -67.2, 61.3],
                  "frame": {
                    "id": "a41c09e2b6f3", "time": 1794204000000,
                    "parts": [{ "id": "faa-tfr", "coverage": ["United States"], "time": 1794204000000 }]
                  },
                  "refreshSeconds": 300,
                  "sourceLayer": "restrictions",
                  "style": { "lineColor": "#ff0000", "lineWidth": 2.0, "fillColor": "#ff0000", "fillOpacity": 0.08,
                             "labelProperty": "label" }
                },
                {
                  "id": "military", "kind": "vector", "category": "airspace",
                  "coverage": ["United States", "United Kingdom"],
                  "title": "Military airspace", "forecast": false,
                  "attribution": [{ "text": "tar1090", "url": "https://github.com/wiedehopf/tar1090" }],
                  "status": "available", "access": "allowed",
                  "tiles": "/api/v1/overlays/military/77d1e0c4a9b2/{z}/{x}/{y}.mvt",
                  "minZoom": 0, "maxZoom": 12, "tileSize": 512,
                  "bounds": [-170.0, 18.0, 10.0, 72.0],
                  "frame": {
                    "id": "77d1e0c4a9b2", "time": 1794200000000,
                    "parts": [{ "id": "faa-sua", "coverage": ["United States"], "time": 1794200000000 }]
                  },
                  "refreshSeconds": 3600,
                  "sourceLayer": "military",
                  "style": { "lineColor": "#4895ef", "lineWidth": 1.5, "fillColor": "#4895ef", "fillOpacity": 0.1,
                             "colorProperty": "color" }
                }
              ]
            }
        """.trimIndent()
    }
}
