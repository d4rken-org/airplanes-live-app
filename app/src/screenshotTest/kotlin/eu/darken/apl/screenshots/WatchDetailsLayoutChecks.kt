package eu.darken.apl.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import java.time.Instant

// Layout checks, rendered once and not shipped as store screenshots

@PreviewTest
@Preview(name = "first-column-360dp", widthDp = 360)
@Composable
fun ActivityHourFirstColumn() = ActivityHourLayoutContent(selectedHour = 0)

@PreviewTest
@Preview(name = "middle-column-360dp", widthDp = 360)
@Composable
fun ActivityHourMiddleColumn() = ActivityHourLayoutContent(selectedHour = 12)

@PreviewTest
@Preview(name = "last-column-360dp", widthDp = 360)
@Composable
fun ActivityHourLastColumn() = ActivityHourLayoutContent(selectedHour = 23)

@PreviewTest
@Preview(name = "last-column-360dp-rtl", widthDp = 360)
@Composable
fun ActivityHourLastColumnRtl() = ActivityHourLayoutContent(selectedHour = 23, rtl = true)

@PreviewTest
@Preview(name = "middle-column-360dp-font130", widthDp = 360, fontScale = 1.3f)
@Composable
fun ActivityHourMiddleColumnLargeFont() = ActivityHourLayoutContent(selectedHour = 12)

@PreviewTest
@Preview(name = "middle-column-360dp-loading", widthDp = 360)
@Composable
fun ActivityHourLoading() = ActivityHourLayoutContent(selectedHour = 12, loading = true)

@PreviewTest
@Preview(name = "tuesday-360dp", widthDp = 360)
@Composable
fun CountChartTuesday() = CountChartLayoutContent(now = LAYOUT_NOW)

@PreviewTest
@Preview(name = "tuesday-360dp-font130", widthDp = 360, fontScale = 1.3f)
@Composable
fun CountChartTuesdayLargeFont() = CountChartLayoutContent(now = LAYOUT_NOW)

@PreviewTest
@Preview(name = "monday-360dp", widthDp = 360)
@Composable
fun CountChartMonday() = CountChartLayoutContent(now = Instant.parse("2026-11-23T10:00:00Z"))

@PreviewTest
@Preview(name = "sunday-360dp", widthDp = 360)
@Composable
fun CountChartSunday() = CountChartLayoutContent(now = Instant.parse("2026-11-22T10:00:00Z"))
