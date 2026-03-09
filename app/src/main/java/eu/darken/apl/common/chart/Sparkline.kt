package eu.darken.apl.common.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.core.cartesian.Zoom
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.common.data.ExtraStore

@Composable
fun Sparkline(
    data: List<ChartPoint>,
    lineColor: Color,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Unspecified,
) {
    if (data.size < 2) {
        Box(modifier)
        return
    }

    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(data) {
        modelProducer.runTransaction {
            lineSeries {
                series(
                    x = data.map { ((it.timestamp.toEpochMilli() - data.first().timestamp.toEpochMilli()) / 60_000L).toDouble() },
                    y = data.map { it.value },
                )
            }
        }
    }

    val chart = @Composable { chartModifier: Modifier ->
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(
                    lineProvider = LineCartesianLayer.LineProvider.series(
                        LineCartesianLayer.rememberLine(
                            fill = LineCartesianLayer.LineFill.single(fill(lineColor)),
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
            ),
            modelProducer = modelProducer,
            modifier = chartModifier,
            scrollState = rememberVicoScrollState(scrollEnabled = false),
            zoomState = rememberVicoZoomState(
                zoomEnabled = false,
                initialZoom = remember { Zoom.Content },
                minZoom = remember { Zoom.Content },
                maxZoom = remember { Zoom.Content },
            ),
        )
    }

    if (backgroundColor != Color.Unspecified) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(4.dp))
                .background(backgroundColor)
                .padding(1.dp),
        ) {
            chart(Modifier.matchParentSize())
        }
    } else {
        chart(modifier)
    }
}
