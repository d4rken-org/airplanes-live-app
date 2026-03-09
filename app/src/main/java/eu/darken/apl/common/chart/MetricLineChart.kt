package eu.darken.apl.common.chart

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
import com.patrykandpatrick.vico.core.cartesian.Zoom
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.compose.common.shader.verticalGradient
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.common.data.ExtraStore
import com.patrykandpatrick.vico.core.common.shader.ShaderProvider
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import eu.darken.apl.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun MetricLineChart(
    title: String,
    data: List<ChartPoint>,
    lineColor: Color,
    modifier: Modifier = Modifier,
    noDataText: String = stringResource(R.string.feeder_chart_no_data),
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
        )

        if (data.size < 2) {
            Text(
                text = noDataText,
                style = MaterialTheme.typography.bodySmall,
            )
            return
        }

        val baseInstant = remember(data) { data.first().timestamp }
        val modelProducer = remember { CartesianChartModelProducer() }
        val dateFormatter = remember { DateTimeFormatter.ofPattern("EEE HH:mm").withZone(ZoneId.systemDefault()) }

        val bottomAxisFormatter = remember(baseInstant) {
            CartesianValueFormatter { _, value, _ ->
                val millis = baseInstant.toEpochMilli() + (value.toLong() * 60_000L)
                dateFormatter.format(Instant.ofEpochMilli(millis))
            }
        }

        LaunchedEffect(data) {
            modelProducer.runTransaction {
                lineSeries {
                    series(
                        x = data.map { ((it.timestamp.toEpochMilli() - baseInstant.toEpochMilli()) / 60_000L).toDouble() },
                        y = data.map { it.value },
                    )
                }
            }
        }

        ProvideVicoTheme(rememberM3VicoTheme()) {
            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberLineCartesianLayer(
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
