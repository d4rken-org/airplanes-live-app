package eu.darken.apl.feeder.core.stats

import eu.darken.apl.account.core.api.FeederDetailResponse
import eu.darken.apl.common.chart.ChartPoint
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import java.time.Instant

enum class FeederChartWindow(val wireKey: String) {
    H24("24h"),
    D30("30d"),
    ;
}

/**
 * One anchored history window. The series map is generic (`family -> metric -> values`) so future
 * server-side families flow through without parsing changes; typed access happens at the UI edge
 * via [chartSegments].
 */
data class FeederHistoryWindow(
    val start: Instant,
    val end: Instant,
    val bucketSeconds: Int,
    private val series: Map<String, Map<String, List<Double?>>>,
) {

    /**
     * Chart points for one metric, split into contiguous segments at null/non-finite buckets so
     * gaps (outages, missing data) render as gaps instead of a misleading bridged line.
     */
    fun chartSegments(familyWireKey: String, metricKey: String): List<List<ChartPoint>> {
        val values = series[familyWireKey]?.get(metricKey) ?: return emptyList()
        return segmentize(values)
    }

    /**
     * Segments for two alternative series of the same quantity, preferring [primaryMetric] and
     * filling its null buckets from [fallbackMetric] — used for the range series pair, where a
     * provenance change mid-window leaves each half in a different series.
     */
    fun mergedChartSegments(
        familyWireKey: String,
        primaryMetric: String,
        fallbackMetric: String,
    ): List<List<ChartPoint>> {
        val primary = series[familyWireKey]?.get(primaryMetric)
        val fallback = series[familyWireKey]?.get(fallbackMetric)
        if (primary == null && fallback == null) return emptyList()
        val size = maxOf(primary?.size ?: 0, fallback?.size ?: 0)
        val merged = (0 until size).map { i -> primary?.getOrNull(i) ?: fallback?.getOrNull(i) }
        return segmentize(merged)
    }

    private fun segmentize(values: List<Double?>): List<List<ChartPoint>> {
        val segments = mutableListOf<List<ChartPoint>>()
        var current = mutableListOf<ChartPoint>()
        values.forEachIndexed { index, value ->
            if (value == null || !value.isFinite()) {
                if (current.isNotEmpty()) segments += current
                current = mutableListOf()
            } else {
                current += ChartPoint(start.plusSeconds(bucketSeconds.toLong() * index), value)
            }
        }
        if (current.isNotEmpty()) segments += current
        return segments
    }

    /** Flat variant for sparklines, where a bridged gap is an acceptable simplification. */
    fun chartPoints(familyWireKey: String, metricKey: String): List<ChartPoint> =
        chartSegments(familyWireKey, metricKey).flatten()

    fun hasData(familyWireKey: String): Boolean =
        series[familyWireKey]?.values?.any { list -> list.any { it != null && it.isFinite() } } == true
}

/** Current provenance of the feeder's range data. */
enum class RangeSource {
    CONFIGURED,
    INFERRED,
    NONE,
    UNKNOWN,
    ;
}

/**
 * The feeder-attested stats lane. History-only by design (no live gauge exists server-side); the
 * "now" proxy is the last bucket of the 24h window. Windows keep their own grid — the server
 * caches this lane independently of the diagnostics history and the anchors may differ.
 */
data class FeederStats(
    val rangeSource: RangeSource,
    val history: Map<FeederChartWindow, FeederHistoryWindow>,
) {
    companion object {
        const val WIRE_KEY = "stats"
        const val METRIC_AIRCRAFT_TOTAL = "aircraft_total"
        const val METRIC_AIRCRAFT_WITH_POS = "aircraft_with_pos"
        const val METRIC_MLAT_SHARE = "mlat_share"
        const val METRIC_RANGE_CONFIGURED = "max_range_configured_nmi"
        const val METRIC_RANGE_INFERRED = "max_range_inferred_nmi"
    }
}

/**
 * Domain view of the feeder detail endpoint. Deliberately does NOT carry the feeder's identity or
 * status — [eu.darken.apl.feeder.core.FeederRepo]'s list stays the single source of truth for
 * those, and consumers combine the two.
 */
data class FeederDetail(
    val live: List<FeederMetricFamily>,
    val history: Map<FeederChartWindow, FeederHistoryWindow>,
    /** Null when unavailable (diagnostics off, or the deployment predates the stats lane). */
    val stats: FeederStats?,
    val fetchedAt: Instant,
)

internal fun FeederDetailResponse.toDomain(fetchedAt: Instant): FeederDetail {
    val families = buildList {
        live?.feed?.let {
            add(
                FeederMetricFamily.Feed(
                    connected = it.connected,
                    messagesPerSec = it.messagesPerSec,
                    positionsPerSec = it.positionsPerSec,
                    rttMs = it.rttMs,
                    connectedForSeconds = it.connectedForSeconds,
                )
            )
        }
        live?.mlat?.let {
            add(
                FeederMetricFamily.Mlat(
                    connected = it.connected,
                    peerCount = it.peerCount,
                    messageRate = it.messageRate,
                    outlierPercent = it.outlierPercent,
                    badSync = it.badSync,
                    interestCount = it.interestCount,
                )
            )
        }
        live?.device?.let { device ->
            add(
                FeederMetricFamily.Device(
                    cpuTemperatureC = device.cpuTemperatureC,
                    memoryUsedPercent = device.memoryUsedPercent,
                    diskUsedPercent = device.diskUsedPercent,
                    wifiRssiDbm = device.wifiRssiDbm,
                    clockSkewSeconds = device.clockSkewSeconds,
                    observedAt = device.observedAt,
                    freshness = deviceFreshnessFromWire(device.freshness),
                    connectionType = device.connectionType,
                    severities = device.severity.entries.mapNotNull { (key, value) ->
                        val metric = healthMetricFromWire(key) ?: return@mapNotNull null
                        val severity = healthSeverityFromWire(value) ?: return@mapNotNull null
                        metric to severity
                    }.toMap(),
                )
            )
        }
    }
    val windows = FeederChartWindow.entries.mapNotNull { window ->
        val wire = history[window.wireKey] ?: return@mapNotNull null
        // A malformed window would produce nonsense timestamps on every chart — drop it entirely.
        if (wire.bucketSeconds <= 0 || wire.start > wire.end) {
            log(TAG, WARN) { "Discarding malformed history window $window: $wire" }
            return@mapNotNull null
        }
        window to FeederHistoryWindow(
            start = wire.start,
            end = wire.end,
            bucketSeconds = wire.bucketSeconds,
            series = wire.series,
        )
    }.toMap()
    val statsBlock = stats?.let { wire ->
        val statsWindows = FeederChartWindow.entries.mapNotNull { window ->
            val statsWire = wire.history[window.wireKey] ?: return@mapNotNull null
            if (statsWire.bucketSeconds <= 0 || statsWire.start > statsWire.end) {
                log(TAG, WARN) { "Discarding malformed stats window $window: $statsWire" }
                return@mapNotNull null
            }
            window to FeederHistoryWindow(
                start = statsWire.start,
                end = statsWire.end,
                bucketSeconds = statsWire.bucketSeconds,
                series = mapOf(FeederStats.WIRE_KEY to statsWire.series),
            )
        }.toMap()
        FeederStats(
            rangeSource = when (wire.rangeSource) {
                "configured" -> RangeSource.CONFIGURED
                "inferred" -> RangeSource.INFERRED
                "none" -> RangeSource.NONE
                else -> RangeSource.UNKNOWN
            },
            history = statsWindows,
        )
    }
    return FeederDetail(live = families, history = windows, stats = statsBlock, fetchedAt = fetchedAt)
}

private val TAG = logTag("Feeder", "Stats", "Mapper")
