package eu.darken.apl.feeder.core.stats

import java.time.Instant

data class ChartPoint(val timestamp: Instant, val value: Double)

data class BeastChartData(val messageRate: List<ChartPoint>)

data class MlatChartData(
    val messageRate: List<ChartPoint>,
    val outlierPercent: List<ChartPoint>,
)

sealed interface ChartState<out T> {
    data object Loading : ChartState<Nothing>
    data class Ready<T>(val data: T) : ChartState<T>
    data object NoData : ChartState<Nothing>
}
