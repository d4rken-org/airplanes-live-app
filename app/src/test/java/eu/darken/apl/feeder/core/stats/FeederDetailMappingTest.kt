package eu.darken.apl.feeder.core.stats

import eu.darken.apl.account.core.api.FeederDetailResponse
import eu.darken.apl.account.core.api.FeederDeviceWire
import eu.darken.apl.account.core.api.FeederFeedWire
import eu.darken.apl.account.core.api.FeederHistoryWindowWire
import eu.darken.apl.account.core.api.FeederLiveWire
import eu.darken.apl.account.core.api.FeederMlatWire
import eu.darken.apl.account.core.api.FeederStatsWindowWire
import eu.darken.apl.account.core.api.FeederStatsWire
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.time.Instant

class FeederDetailMappingTest : BaseTest() {

    private val start = Instant.parse("2026-07-18T12:00:00Z")
    private val end = Instant.parse("2026-07-19T12:00:00Z")
    private val now = Instant.parse("2026-07-19T13:00:00Z")

    private fun window(
        bucketSeconds: Int = 1440,
        series: Map<String, Map<String, List<Double?>>> = emptyMap(),
        start: Instant = this.start,
        end: Instant = this.end,
    ) = FeederHistoryWindowWire(start = start, end = end, bucketSeconds = bucketSeconds, series = series)

    @Test
    fun `families map in order and absent families are absent`() {
        val detail = FeederDetailResponse(
            feederId = "abc",
            live = FeederLiveWire(
                feed = FeederFeedWire(connected = true, messagesPerSec = 5.0),
                mlat = FeederMlatWire(connected = false),
                device = null,
            ),
        ).toDomain(now)

        detail.live shouldHaveSize 2
        detail.live.family<FeederMetricFamily.Feed>().shouldNotBeNull().messagesPerSec shouldBe 5.0
        detail.live.family<FeederMetricFamily.Mlat>().shouldNotBeNull().connected shouldBe false
        detail.live.family<FeederMetricFamily.Device>() shouldBe null
        detail.fetchedAt shouldBe now
    }

    @Test
    fun `chart segments split at null and non-finite buckets`() {
        val detail = FeederDetailResponse(
            feederId = "abc",
            history = mapOf(
                "24h" to window(
                    series = mapOf(
                        "feed" to mapOf(
                            "messages_per_sec" to listOf(1.0, 2.0, null, 4.0, Double.NaN, 6.0, 7.0),
                        )
                    )
                ),
            ),
        ).toDomain(now)

        val segments = detail.history.getValue(FeederChartWindow.H24)
            .chartSegments(FeederMetricFamily.Feed.WIRE_KEY, "messages_per_sec")

        segments shouldHaveSize 3
        segments[0].map { it.value } shouldBe listOf(1.0, 2.0)
        segments[1].map { it.value } shouldBe listOf(4.0)
        segments[2].map { it.value } shouldBe listOf(6.0, 7.0)
        // Timestamps derive from the bucket index, so gaps don't shift later points.
        segments[1][0].timestamp shouldBe start.plusSeconds(1440L * 3)
        segments[2][0].timestamp shouldBe start.plusSeconds(1440L * 5)
    }

    @Test
    fun `flat chart points bridge gaps for sparklines`() {
        val detail = FeederDetailResponse(
            feederId = "abc",
            history = mapOf("24h" to window(series = mapOf("feed" to mapOf("messages_per_sec" to listOf(1.0, null, 3.0))))),
        ).toDomain(now)

        detail.history.getValue(FeederChartWindow.H24)
            .chartPoints(FeederMetricFamily.Feed.WIRE_KEY, "messages_per_sec")
            .map { it.value } shouldBe listOf(1.0, 3.0)
    }

    @Test
    fun `malformed windows are discarded`() {
        val detail = FeederDetailResponse(
            feederId = "abc",
            history = mapOf(
                "24h" to window(bucketSeconds = 0),
                "30d" to window(start = end, end = start),
            ),
        ).toDomain(now)

        detail.history shouldNotContainKey FeederChartWindow.H24
        detail.history shouldNotContainKey FeederChartWindow.D30
    }

    @Test
    fun `unknown window keys are ignored, known ones kept`() {
        val detail = FeederDetailResponse(
            feederId = "abc",
            history = mapOf(
                "24h" to window(),
                "7d" to window(bucketSeconds = 7200),
            ),
        ).toDomain(now)

        detail.history shouldContainKey FeederChartWindow.H24
        detail.history.keys shouldHaveSize 1
    }

    @Test
    fun `hasData reflects only finite values`() {
        val detail = FeederDetailResponse(
            feederId = "abc",
            history = mapOf(
                "24h" to window(
                    series = mapOf(
                        "device" to mapOf("cpu_temperature_c" to listOf(null, null)),
                        "feed" to mapOf("messages_per_sec" to listOf(null, 2.0)),
                    )
                ),
            ),
        ).toDomain(now)

        val h24 = detail.history.getValue(FeederChartWindow.H24)
        h24.hasData(FeederMetricFamily.Feed.WIRE_KEY) shouldBe true
        h24.hasData(FeederMetricFamily.Device.WIRE_KEY) shouldBe false
        h24.hasData("aircraft_stats") shouldBe false
    }

    @Test
    fun `missing metric yields empty segments`() {
        val detail = FeederDetailResponse(feederId = "abc", history = mapOf("24h" to window())).toDomain(now)
        detail.history.getValue(FeederChartWindow.H24)
            .chartSegments("feed", "messages_per_sec").shouldBeEmpty()
    }

    @Test
    fun `device severities and freshness map from the wire`() {
        val detail = FeederDetailResponse(
            feederId = "abc",
            live = FeederLiveWire(
                device = FeederDeviceWire(
                    cpuTemperatureC = 81.0,
                    freshness = "stale",
                    severity = mapOf(
                        "cpu_temperature_c" to "err",
                        "memory_used_percent" to "ok",
                        "disk_used_percent" to "warn",
                        "wifi_rssi_dbm" to null,
                        "brand_new_metric" to "warn",
                    ),
                ),
            ),
        ).toDomain(now)

        val device = detail.live.family<FeederMetricFamily.Device>().shouldNotBeNull()
        device.freshness shouldBe DeviceFreshness.STALE
        device.severities shouldBe mapOf(
            HealthMetric.CPU_TEMPERATURE to HealthSeverity.CRIT,
            HealthMetric.MEMORY_AVAILABLE to HealthSeverity.OK,
            HealthMetric.DISK_AVAILABLE to HealthSeverity.WARN,
        )
    }

    @Test
    fun `stats block maps with its own windows and range provenance`() {
        val detail = FeederDetailResponse(
            feederId = "abc",
            stats = FeederStatsWire(
                rangeSource = "inferred",
                history = mapOf(
                    "24h" to FeederStatsWindowWire(
                        start = start, end = end, bucketSeconds = 1440,
                        series = mapOf("aircraft_total" to listOf(10.0, null, 12.0)),
                    ),
                    "30d" to FeederStatsWindowWire(start = end, end = start, bucketSeconds = 43200),
                ),
            ),
        ).toDomain(now)

        val stats = detail.stats.shouldNotBeNull()
        stats.rangeSource shouldBe RangeSource.INFERRED
        stats.history.keys shouldBe setOf(FeederChartWindow.H24) // malformed 30d discarded
        stats.history.getValue(FeederChartWindow.H24)
            .chartPoints(FeederStats.WIRE_KEY, FeederStats.METRIC_AIRCRAFT_TOTAL)
            .map { it.value } shouldBe listOf(10.0, 12.0)
    }

    @Test
    fun `absent stats maps to null and unknown range source is tolerated`() {
        FeederDetailResponse(feederId = "abc").toDomain(now).stats shouldBe null
        FeederDetailResponse(feederId = "abc", stats = FeederStatsWire(rangeSource = "wormhole"))
            .toDomain(now).stats.shouldNotBeNull().rangeSource shouldBe RangeSource.UNKNOWN
    }

    @Test
    fun `merged range segments prefer primary and fill from fallback`() {
        val window = FeederDetailResponse(
            feederId = "abc",
            stats = FeederStatsWire(
                rangeSource = "configured",
                history = mapOf(
                    "24h" to FeederStatsWindowWire(
                        start = start, end = end, bucketSeconds = 1440,
                        series = mapOf(
                            "max_range_configured_nmi" to listOf(100.0, null, null, 110.0),
                            "max_range_inferred_nmi" to listOf(null, 90.0, null, 95.0),
                        ),
                    ),
                ),
            ),
        ).toDomain(now).stats!!.history.getValue(FeederChartWindow.H24)

        val segments = window.mergedChartSegments(
            FeederStats.WIRE_KEY,
            FeederStats.METRIC_RANGE_CONFIGURED,
            FeederStats.METRIC_RANGE_INFERRED,
        )
        // bucket 0 primary, bucket 1 fallback, bucket 2 gap, bucket 3 primary wins over fallback
        segments.map { seg -> seg.map { it.value } } shouldBe listOf(listOf(100.0, 90.0), listOf(110.0))
    }
}
