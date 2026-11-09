package eu.darken.apl.feeder.ui.actions

import eu.darken.apl.common.chart.ChartPoint
import eu.darken.apl.common.chart.ChartState
import eu.darken.apl.server.api.FeederHistoryHour
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.time.Instant

class FeederServerChartsTest : BaseTest() {

    private fun hour(
        index: Int,
        rate: Double? = null,
        peers: Double? = null,
        outliers: Double? = null,
    ) = FeederHistoryHour(
        hour = HOUR_MS * index,
        msgsPerSecondAvg = rate,
        mlatPeersAvg = peers,
        mlatOutlierPercentAvg = outliers,
        isFinal = true,
    )

    private fun point(index: Int, value: Double) = ChartPoint(Instant.ofEpochMilli(HOUR_MS * index), value)

    @Test
    fun `each chart only gets the hours that have its metric`() {
        val charts = FeederServerCharts.from(
            listOf(
                hour(0, rate = 100.0, peers = 4.0, outliers = 1.0),
                hour(1, rate = 120.0),
                hour(2, peers = 5.0, outliers = 2.0),
            )
        )

        charts.messageRate shouldBe ChartState.Ready(listOf(point(0, 100.0), point(1, 120.0)))
        charts.mlatPeers shouldBe ChartState.Ready(listOf(point(0, 4.0), point(2, 5.0)))
        charts.mlatOutlierPercent shouldBe ChartState.Ready(listOf(point(0, 1.0), point(2, 2.0)))
    }

    @Test
    fun `outlier percentages are kept between 0 and 100`() {
        val charts = FeederServerCharts.from(
            listOf(
                hour(0, outliers = -3.0),
                hour(1, outliers = 140.0),
            )
        )

        charts.mlatOutlierPercent shouldBe ChartState.Ready(listOf(point(0, 0.0), point(1, 100.0)))
    }

    @Test
    fun `values that are not finite are skipped`() {
        val charts = FeederServerCharts.from(
            listOf(
                hour(0, rate = 10.0, outliers = Double.POSITIVE_INFINITY),
                hour(1, rate = Double.NaN, outliers = 5.0),
                hour(2, rate = 12.0, outliers = 6.0),
            )
        )

        charts.messageRate shouldBe ChartState.Ready(listOf(point(0, 10.0), point(2, 12.0)))
        charts.mlatOutlierPercent shouldBe ChartState.Ready(listOf(point(1, 5.0), point(2, 6.0)))
    }

    @Test
    fun `a chart with fewer than two points has no data`() {
        val charts = FeederServerCharts.from(
            listOf(
                hour(0, rate = 10.0, peers = 3.0),
                hour(1, rate = 11.0),
            )
        )

        charts.messageRate shouldBe ChartState.Ready(listOf(point(0, 10.0), point(1, 11.0)))
        charts.mlatPeers shouldBe ChartState.NoData
        charts.mlatOutlierPercent shouldBe ChartState.NoData
    }

    @Test
    fun `no history has no data`() {
        FeederServerCharts.from(emptyList()) shouldBe FeederServerCharts.NO_DATA
    }

    companion object {
        private const val HOUR_MS = 3_600_000L
    }
}
