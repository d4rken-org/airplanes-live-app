package eu.darken.apl.feeder.ui.actions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.apl.R
import eu.darken.apl.common.chart.ChartPoint
import eu.darken.apl.common.chart.ChartState
import eu.darken.apl.common.chart.MetricLineChart
import eu.darken.apl.feeder.core.Feeder
import eu.darken.apl.feeder.core.server.feederCheckNameRes
import eu.darken.apl.feeder.core.server.feederProblemNameRes
import eu.darken.apl.feeder.ui.feederStatusText

/** What the server reports about [feeder]; without server monitoring only that it is unavailable. */
@Composable
fun FeederServerSection(
    feeder: Feeder,
    sampleIntervalSeconds: Int?,
    modifier: Modifier = Modifier,
) {
    val entry = feeder.server?.entry
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.feeder_server_section_label),
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(Modifier.height(4.dp))
        Text(text = feederStatusText(feeder), style = MaterialTheme.typography.bodyMedium)

        entry?.problems.orEmpty()
            .map { stringResource(feederProblemNameRes(it)) }
            .distinct()
            .forEach { name ->
                Text(
                    text = name.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

        val uncheckedNames = entry?.insufficientEvidence.orEmpty()
            .map { stringResource(feederCheckNameRes(it)) }
            .distinct()
        if (uncheckedNames.isNotEmpty()) {
            Text(
                text = stringResource(
                    R.string.feeder_server_insufficient_evidence_x,
                    uncheckedNames.joinToString(stringResource(R.string.common_list_separator)),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        sampleIntervalSeconds?.takeIf { it > 0 }?.let { seconds ->
            val minutes = (seconds / 60).coerceAtLeast(1)
            Text(
                text = pluralStringResource(R.plurals.feeder_server_sample_interval_x, minutes, minutes),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** The sheet's charts from the server's hourly history. */
@Composable
fun FeederServerChartsSection(
    charts: FeederServerCharts,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        ServerChart(
            title = stringResource(R.string.feeder_chart_beast_rate_label),
            state = charts.messageRate,
            lineColor = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        ServerChart(
            title = stringResource(R.string.feeder_chart_mlat_peers_label),
            state = charts.mlatPeers,
            lineColor = MaterialTheme.colorScheme.tertiary,
        )
        Spacer(Modifier.height(16.dp))
        ServerChart(
            title = stringResource(R.string.feeder_chart_mlat_outlier_label),
            state = charts.mlatOutlierPercent,
            lineColor = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun ServerChart(
    title: String,
    state: ChartState<List<ChartPoint>>,
    lineColor: Color,
) {
    when (state) {
        is ChartState.Loading -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }

        is ChartState.Ready -> MetricLineChart(
            title = title,
            data = state.data,
            lineColor = lineColor,
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
        )

        // Titled, so it is clear which metric lacks data
        is ChartState.NoData -> MetricLineChart(
            title = title,
            data = emptyList(),
            lineColor = lineColor,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
