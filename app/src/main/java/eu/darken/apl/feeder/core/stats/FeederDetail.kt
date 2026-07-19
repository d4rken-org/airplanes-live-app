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

/**
 * Domain view of the feeder detail endpoint. Deliberately does NOT carry the feeder's identity or
 * status — [eu.darken.apl.feeder.core.FeederRepo]'s list stays the single source of truth for
 * those, and consumers combine the two.
 */
data class FeederDetail(
    val live: List<FeederMetricFamily>,
    val history: Map<FeederChartWindow, FeederHistoryWindow>,
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
        live?.device?.let {
            add(
                FeederMetricFamily.Device(
                    cpuTemperatureC = it.cpuTemperatureC,
                    memoryUsedPercent = it.memoryUsedPercent,
                    diskUsedPercent = it.diskUsedPercent,
                    wifiRssiDbm = it.wifiRssiDbm,
                    clockSkewSeconds = it.clockSkewSeconds,
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
    return FeederDetail(live = families, history = windows, fetchedAt = fetchedAt)
}

private val TAG = logTag("Feeder", "Stats", "Mapper")
