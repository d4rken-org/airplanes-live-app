package eu.darken.apl.feeder.ui.detail

import androidx.annotation.StringRes
import eu.darken.apl.R
import eu.darken.apl.feeder.core.stats.FeederMetricFamily

/**
 * UI-side chart definitions: which history metrics each family renders and how they're labelled.
 * Kept out of core so string resources stay at the UI edge.
 */
internal data class MetricChartDef(
    val metricKey: String,
    @param:StringRes val labelRes: Int,
)

internal fun chartDefsFor(family: FeederMetricFamily): List<MetricChartDef> = when (family) {
    is FeederMetricFamily.Feed -> listOf(
        MetricChartDef("messages_per_sec", R.string.feeder_metric_message_rate),
        MetricChartDef("positions_per_sec", R.string.feeder_metric_position_rate),
        MetricChartDef("rtt_ms", R.string.feeder_metric_rtt),
    )

    is FeederMetricFamily.Mlat -> listOf(
        MetricChartDef("message_rate", R.string.feeder_metric_message_rate),
        MetricChartDef("peer_count", R.string.feeder_metric_peers),
        MetricChartDef("outlier_percent", R.string.feeder_metric_outliers),
    )

    is FeederMetricFamily.Device -> listOf(
        MetricChartDef("cpu_temperature_c", R.string.feeder_health_metric_cpu),
        MetricChartDef("memory_used_percent", R.string.feeder_metric_memory_used),
        MetricChartDef("disk_used_percent", R.string.feeder_metric_disk_used),
        MetricChartDef("wifi_rssi_dbm", R.string.feeder_health_metric_wifi),
        MetricChartDef("clock_skew_seconds", R.string.feeder_health_metric_clock),
    )
}
