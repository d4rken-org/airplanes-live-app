package eu.darken.apl.watch.ui.chart

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import com.patrykandpatrick.vico.core.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.core.cartesian.CartesianMeasuringContext
import com.patrykandpatrick.vico.core.cartesian.Zoom
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.ColumnCartesianLayerModel
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.layer.CartesianLayerDimensions
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.core.common.component.LineComponent
import com.patrykandpatrick.vico.core.common.component.TextComponent
import com.patrykandpatrick.vico.core.common.data.ExtraStore
import com.patrykandpatrick.vico.core.common.shape.CorneredShape
import com.patrykandpatrick.vico.core.common.shape.Shape
import eu.darken.apl.R
import eu.darken.apl.common.chart.ChartPoint
import eu.darken.apl.common.compose.Preview2
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.common.time.bestDateTimeFormatter
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** One column per local day of the last 30 days, each the day's highest hourly aircraft count. */
@Composable
fun WatchCountChart(
    counts: List<ChartPoint>,
    now: Instant,
    zone: ZoneId,
    modifier: Modifier = Modifier,
) {
    val today = now.atZone(zone).toLocalDate()
    val peaks = remember(counts, today, zone) { WatchDailyPeaks.build(counts, now, zone) }

    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.watch_details_counts_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )

        if (!peaks.hasValues) {
            Text(
                text = stringResource(R.string.watch_chart_no_data),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            peaks.busiestHour
                ?.takeIf { it.value > 0 }
                ?.let { BusiestHour(hour = it, zone = zone, modifier = Modifier.padding(top = 4.dp)) }

            DailyColumns(
                peaks = peaks,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(160.dp),
            )
        }
    }
}

@Composable
private fun BusiestHour(hour: ChartPoint, zone: ZoneId, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val is24Hour = DateFormat.is24HourFormat(context)
    val formatter = remember(locale, is24Hour, zone) {
        bestDateTimeFormatter(locale, if (is24Hour) "EEEMMMdHm" else "EEEMMMdhm").withZone(zone)
    }
    val count = remember(locale, hour.value) {
        NumberFormat.getIntegerInstance(locale).format(hour.value.roundToLong())
    }

    Column(modifier = modifier) {
        Text(text = count, style = MaterialTheme.typography.headlineSmall)
        Text(
            text = stringResource(R.string.watch_details_counts_busiest_x, formatter.format(hour.timestamp)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DailyColumns(peaks: WatchDailyPeaks, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val locale = LocalConfiguration.current.locales[0]
    val todayLabel = stringResource(R.string.watch_details_activity_today)
    val dateFormatter = remember(locale) { bestDateTimeFormatter(locale, "MMMd") }

    val scale = remember(peaks) { YScale.of(peaks.days.mapNotNull { it.peak }.maxOrNull() ?: 0.0) }
    val stub = scale.max * ZERO_STUB_FRACTION

    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(peaks, stub) {
        val present = peaks.days.withIndex().filter { it.value.peak != null }
        modelProducer.runTransaction {
            columnSeries {
                series(
                    x = present.map { it.index },
                    // Keeps zero days visible as a stub; the headline reads the real values from peaks.
                    y = present.map { it.value.peak!!.coerceAtLeast(stub) },
                )
            }
        }
    }

    val columnShape = remember { CorneredShape.rounded(topLeftDp = 3f, topRightDp = 3f) }
    val column = rememberLineComponent(fill = fill(colors.primary), thickness = COLUMN_WIDTH, shape = columnShape)
    val todayColumn = rememberLineComponent(
        fill = fill(colors.primary.copy(alpha = 0.45f)),
        thickness = COLUMN_WIDTH,
        shape = columnShape,
    )
    val todayX = peaks.todayIndex.toDouble()
    val columnProvider = remember(column, todayColumn, todayX) {
        object : ColumnCartesianLayer.ColumnProvider {
            override fun getColumn(
                entry: ColumnCartesianLayerModel.Entry,
                seriesIndex: Int,
                extraStore: ExtraStore,
            ): LineComponent = if (entry.x == todayX) todayColumn else column

            override fun getWidestSeriesColumn(seriesIndex: Int, extraStore: ExtraStore): LineComponent = column
        }
    }
    val rangeProvider = remember(todayX, scale) {
        CartesianLayerRangeProvider.fixed(minX = 0.0, maxX = todayX, minY = 0.0, maxY = scale.max)
    }

    val dayLabel = remember(peaks, todayLabel, dateFormatter) {
        { x: Double ->
            val index = x.roundToInt().coerceIn(0, peaks.todayIndex)
            if (index == peaks.todayIndex) todayLabel else dateFormatter.format(peaks.days[index].date)
        }
    }
    val bottomLabel = rememberAxisLabelComponent()
    val bottomFormatter = remember(dayLabel) { CartesianValueFormatter { _, x, _ -> dayLabel(x) } }
    val bottomPlacer = remember(peaks, bottomLabel, dayLabel) {
        val mondays = peaks.days.indices
            .filter { it != peaks.todayIndex && peaks.days[it].date.dayOfWeek == DayOfWeek.MONDAY }
            .map { it.toDouble() }
        DayLabelPlacer(candidates = mondays + todayX, label = bottomLabel, text = dayLabel)
    }
    val startPlacer = remember(scale) { VerticalAxis.ItemPlacer.step({ scale.step }) }
    val xStep = remember { { _: CartesianChartModel -> 1.0 } }

    ProvideVicoTheme(rememberM3VicoTheme()) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberColumnCartesianLayer(
                    columnProvider = columnProvider,
                    columnCollectionSpacing = COLUMN_SPACING,
                    rangeProvider = rangeProvider,
                ),
                startAxis = VerticalAxis.rememberStart(
                    line = null,
                    tick = null,
                    tickLength = 0.dp,
                    guideline = rememberAxisGuidelineComponent(
                        fill = fill(colors.outlineVariant),
                        shape = Shape.Rectangle,
                    ),
                    itemPlacer = startPlacer,
                ),
                bottomAxis = HorizontalAxis.rememberBottom(
                    label = bottomLabel,
                    valueFormatter = bottomFormatter,
                    guideline = null,
                    itemPlacer = bottomPlacer,
                ),
                getXStep = xStep,
            ),
            modelProducer = modelProducer,
            modifier = modifier,
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

/** A y-range from 0 split into at most three round steps, e.g. a peak of 37 gives 0, 20, 40. */
private data class YScale(val step: Double, val max: Double) {
    companion object {
        fun of(maxValue: Double): YScale {
            val top = max(maxValue, 1.0)
            var magnitude = 1.0
            while (true) {
                for (factor in STEP_FACTORS) {
                    val step = factor * magnitude
                    val intervals = ceil(top / step)
                    if (intervals <= MAX_INTERVALS) return YScale(step = step, max = intervals * step)
                }
                magnitude *= 10
            }
        }

        private val STEP_FACTORS = doubleArrayOf(1.0, 2.0, 5.0)
        private const val MAX_INTERVALS = 3
    }
}

/**
 * Labels "Today" and every Monday that fits. Vico 2.1.2 (`HorizontalAxis.drawUnderLayers`) truncates each label to
 * the distance to its neighbouring labels, and the first one to twice its distance to the layer's start, so a
 * Monday is only placed where both it and its right neighbour fit.
 */
private class DayLabelPlacer(
    private val candidates: List<Double>,
    private val label: TextComponent,
    private val text: (Double) -> CharSequence,
) : HorizontalAxis.ItemPlacer {

    override fun getShiftExtremeLines(context: CartesianDrawingContext): Boolean = false

    override fun getLastLabelValue(context: CartesianMeasuringContext, maxLabelWidth: Float): Double =
        candidates.last()

    override fun getLabelValues(
        context: CartesianDrawingContext,
        visibleXRange: ClosedFloatingPointRange<Double>,
        fullXRange: ClosedFloatingPointRange<Double>,
        maxLabelWidth: Float,
    ): List<Double> = with(context) {
        val gap = LABEL_GAP_DP * density
        fun offset(x: Double) = ((x - fullXRange.start) / ranges.xStep).toFloat() * layerDimensions.xSpacing
        fun width(x: Double) = label.getWidth(context = this, text = text(x), pad = true)

        val kept = ArrayDeque<Double>()
        var next = candidates.last()
        var nextWidth = width(next)
        kept.addFirst(next)
        for (x in candidates.dropLast(1).asReversed()) {
            val labelWidth = width(x)
            val fitsStart = 2 * offset(x) >= labelWidth + gap
            val fitsNext = offset(next) - offset(x) >= max(labelWidth, nextWidth) + gap
            if (fitsStart && fitsNext) {
                kept.addFirst(x)
                next = x
                nextWidth = labelWidth
            }
        }
        kept.toList()
    }

    override fun getLineValues(
        context: CartesianDrawingContext,
        visibleXRange: ClosedFloatingPointRange<Double>,
        fullXRange: ClosedFloatingPointRange<Double>,
        maxLabelWidth: Float,
    ): List<Double> = candidates

    override fun getWidthMeasurementLabelValues(
        context: CartesianMeasuringContext,
        layerDimensions: CartesianLayerDimensions,
        fullXRange: ClosedFloatingPointRange<Double>,
    ): List<Double> = candidates

    override fun getHeightMeasurementLabelValues(
        context: CartesianMeasuringContext,
        layerDimensions: CartesianLayerDimensions,
        fullXRange: ClosedFloatingPointRange<Double>,
        maxLabelWidth: Float,
    ): List<Double> = candidates

    override fun getStartLayerMargin(
        context: CartesianMeasuringContext,
        layerDimensions: CartesianLayerDimensions,
        tickThickness: Float,
        maxLabelWidth: Float,
    ): Float = (tickThickness / 2 - layerDimensions.unscalableStartPadding).coerceAtLeast(0f)

    override fun getEndLayerMargin(
        context: CartesianMeasuringContext,
        layerDimensions: CartesianLayerDimensions,
        tickThickness: Float,
        maxLabelWidth: Float,
    ): Float = (tickThickness / 2 - layerDimensions.unscalableEndPadding).coerceAtLeast(0f)
}

private val COLUMN_WIDTH = 8.dp
private val COLUMN_SPACING = 4.dp
private const val ZERO_STUB_FRACTION = 0.03
private const val LABEL_GAP_DP = 6f

@Preview2
@Composable
private fun WatchCountChartPreview() {
    val zone = ZoneOffset.UTC
    val now = Instant.parse("2026-11-04T14:30:00Z")
    val todayStart = now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()
    val missingDays = setOf(5, 6, 17)
    val counts = (0 until 30).flatMap { daysAgo ->
        val dayStart = todayStart.minus(Duration.ofDays(daysAgo.toLong()))
        (0 until 24)
            .map { hour -> dayStart.plus(Duration.ofHours(hour.toLong())) }
            .filter { it <= now && daysAgo !in missingDays }
            .map { at ->
                val hour = at.atZone(zone).hour
                val value = if (daysAgo == 11) 0 else (hour * 7 + daysAgo * 13) % 23 + (if (hour in 8..20) 6 else 0)
                ChartPoint(at, value.toDouble())
            }
    }
    PreviewWrapper {
        WatchCountChart(counts = counts, now = now, zone = zone, modifier = Modifier.padding(16.dp))
    }
}
