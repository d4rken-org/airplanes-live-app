package eu.darken.apl.feeder.core.stats

import eu.darken.apl.common.chart.ChartPoint

data class BeastChartData(val messageRate: List<ChartPoint>)

data class MlatChartData(
    val messageRate: List<ChartPoint>,
    val outlierPercent: List<ChartPoint>,
)
