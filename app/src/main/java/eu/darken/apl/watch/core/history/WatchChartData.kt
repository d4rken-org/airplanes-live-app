package eu.darken.apl.watch.core.history

import eu.darken.apl.common.chart.ChartPoint
import java.time.Instant

data class WatchCountChartData(val counts: List<ChartPoint>)

data class WatchActivityData(val checks: List<WatchActivityCheck>)

data class WatchActivityCheck(val timestamp: Instant, val aircraftCount: Int)
