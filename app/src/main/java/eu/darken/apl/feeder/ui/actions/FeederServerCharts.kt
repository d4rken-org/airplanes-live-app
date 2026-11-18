package eu.darken.apl.feeder.ui.actions

import eu.darken.apl.common.chart.ChartPoint
import eu.darken.apl.common.chart.ChartState
import eu.darken.apl.server.api.FeederHistoryHour
import java.time.Instant

/** The sheet's charts in server mode, from the server's hourly history. */
data class FeederServerCharts(
    val messageRate: ChartState<List<ChartPoint>>,
    val mlatPeers: ChartState<List<ChartPoint>>,
    val mlatOutlierPercent: ChartState<List<ChartPoint>>,
    val uptimePercent: ChartState<List<ChartPoint>>,
    val latencyMs: ChartState<List<ChartPoint>>,
) {
    companion object {
        val LOADING = FeederServerCharts(
            ChartState.Loading,
            ChartState.Loading,
            ChartState.Loading,
            ChartState.Loading,
            ChartState.Loading,
        )
        val NO_DATA = FeederServerCharts(
            ChartState.NoData,
            ChartState.NoData,
            ChartState.NoData,
            ChartState.NoData,
            ChartState.NoData,
        )

        /** Each chart only has points for the hours that have its metric. */
        fun from(hours: List<FeederHistoryHour>) = FeederServerCharts(
            messageRate = hours.points { it.msgsPerSecondAvg }.toChartState(),
            mlatPeers = hours.points { it.mlatPeersAvg }.toChartState(),
            mlatOutlierPercent = hours.points { it.mlatOutlierPercentAvg }
                .map { it.copy(value = it.value.coerceIn(0.0, 100.0)) }
                .toChartState(),
            uptimePercent = hours.points { it.uptimePercent() }
                .map { it.copy(value = it.value.coerceIn(0.0, 100.0)) }
                .toChartState(),
            latencyMs = hours.points { it.rttAvgMs }.toChartState(),
        )

        private fun FeederHistoryHour.uptimePercent(): Double? {
            val connected = connectedSamples ?: return null
            val total = samples?.takeIf { it > 0 } ?: return null
            return connected.toDouble() / total * 100.0
        }

        private fun List<FeederHistoryHour>.points(metric: (FeederHistoryHour) -> Double?): List<ChartPoint> =
            mapNotNull { hour ->
                val value = metric(hour)?.takeIf { it.isFinite() } ?: return@mapNotNull null
                ChartPoint(Instant.ofEpochMilli(hour.hour), value)
            }

        private fun List<ChartPoint>.toChartState(): ChartState<List<ChartPoint>> =
            if (size >= 2) ChartState.Ready(this) else ChartState.NoData
    }
}
