package eu.darken.apl.feeder.ui.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.compose.common.shader.verticalGradient
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import com.patrykandpatrick.vico.core.cartesian.Zoom
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.common.data.ExtraStore
import com.patrykandpatrick.vico.core.common.shader.ShaderProvider
import eu.darken.apl.R
import eu.darken.apl.common.chart.ChartPoint
import eu.darken.apl.feeder.core.stats.FeederChartWindow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Line chart rendering pre-segmented series: each segment is its own vico series, so null buckets
 * (outages, missing data) show as visible gaps instead of a misleading bridged line. Axis labels
 * are window-aware — a 30 day span labelled `EEE HH:mm` would be ambiguous.
 */
@Composable
internal fun GapAwareMetricChart(
    title: String,
    segments: List<List<ChartPoint>>,
    window: FeederChartWindow,
    lineColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
        )

        val allPoints = remember(segments) { segments.flatten() }
        if (allPoints.size < 2) {
            Text(
                text = stringResource(R.string.feeder_chart_no_data),
                style = MaterialTheme.typography.bodySmall,
            )
            return
        }

        val baseInstant = remember(allPoints) { allPoints.first().timestamp }
        val modelProducer = remember { CartesianChartModelProducer() }
        val dateFormatter = remember(window) {
            val pattern = when (window) {
                FeederChartWindow.H24 -> "EEE HH:mm"
                FeederChartWindow.D30 -> "dd. MMM"
            }
            DateTimeFormatter.ofPattern(pattern).withZone(ZoneId.systemDefault())
        }

        val bottomAxisFormatter = remember(baseInstant, dateFormatter) {
            CartesianValueFormatter { _, value, _ ->
                val millis = baseInstant.toEpochMilli() + (value.toLong() * 60_000L)
                dateFormatter.format(Instant.ofEpochMilli(millis))
            }
        }

        LaunchedEffect(segments) {
            modelProducer.runTransaction {
                lineSeries {
                    segments.filter { it.size >= 2 }.forEach { segment ->
                        series(
                            x = segment.map { ((it.timestamp.toEpochMilli() - baseInstant.toEpochMilli()) / 60_000L).toDouble() },
                            y = segment.map { it.value },
                        )
                    }
                }
            }
        }

        ProvideVicoTheme(rememberM3VicoTheme()) {
            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberLineCartesianLayer(
                        // A single line spec applies to every segment — same metric, same style.
                        lineProvider = LineCartesianLayer.LineProvider.series(
                            LineCartesianLayer.rememberLine(
                                fill = LineCartesianLayer.LineFill.single(fill(lineColor)),
                                areaFill = LineCartesianLayer.AreaFill.single(
                                    fill(
                                        ShaderProvider.verticalGradient(
                                            arrayOf(lineColor.copy(alpha = 0.3f), Color.Transparent)
                                        )
                                    )
                                ),
                            )
                        ),
                        rangeProvider = remember {
                            object : CartesianLayerRangeProvider {
                                override fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore) = minY
                                override fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore) =
                                    if (minY == maxY) maxY + 1.0 else maxY
                            }
                        },
                    ),
                    startAxis = VerticalAxis.rememberStart(),
                    bottomAxis = HorizontalAxis.rememberBottom(
                        valueFormatter = bottomAxisFormatter,
                    ),
                ),
                modelProducer = modelProducer,
                modifier = Modifier.fillMaxWidth(),
                scrollState = rememberVicoScrollState(scrollEnabled = false),
                zoomState = rememberVicoZoomState(
                    zoomEnabled = false,
                    initialZoom = remember { Zoom.Content },
                    minZoom = remember { Zoom.Content },
                    maxZoom = remember { Zoom.Content },
                ),
            )
        }
    }
}
