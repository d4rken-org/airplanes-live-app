package eu.darken.apl.airport.ui.flights

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.darken.apl.airport.core.airportFlightListFixture
import eu.darken.apl.common.compose.PreviewWrapper
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeLessThanOrEqual
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.robolectric.annotation.GraphicsMode
import testhelper.BaseComposeTest
import java.time.Instant
import java.time.ZoneId

/**
 * How a row's second line gives way when it is too narrow. Needs real text metrics, so it draws natively and
 * sizes each row from what its texts measure at full width.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AirportFlightRowFitTest : BaseComposeTest() {

    /** 30 seconds after the fixture's evaluation. */
    private val now = Instant.ofEpochMilli(1794571230000)

    private var width by mutableStateOf(WIDE)

    /** The airline's first three characters and "…", in the row's style and font scale. */
    private var shortestAirlineWidth = 0

    /** Measures a text in the row's style and font scale. */
    private var rowTextWidth: (String) -> Int = { error("setRow() first") }

    private fun setRow(key: String, shortestAirline: String, fontScale: Float = 1f) {
        val display = AirportFlightsDisplay.from(
            list = airportFlightListFixture("answered-airlines.json"),
            airportTimeZone = null,
            now = now,
            deviceZone = ZoneId.of("UTC"),
        ).let { display ->
            display.copy(
                sections = display.sections.map { section ->
                    section.copy(rows = section.rows.filter { it.id.endsWith("/$key") })
                }
            )
        }
        val state = AirportFlightsUiState.Loaded(display = display, selected = display.selection(null))

        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                PreviewWrapper {
                    val measurer = rememberTextMeasurer()
                    val style = MaterialTheme.typography.bodyMedium
                    rowTextWidth = { text ->
                        measurer.measure(text = text, style = style, maxLines = 1, softWrap = false).size.width
                    }
                    shortestAirlineWidth = rowTextWidth(shortestAirline)
                    Box(modifier = Modifier.requiredWidth(width)) {
                        LazyColumn(modifier = Modifier.testTag(LIST)) {
                            airportFlights(
                                state = state,
                                onShowFlights = {},
                                onRefresh = {},
                                onShowMore = {},
                                onSelectSection = {},
                                onRetry = {},
                                onReload = {},
                                onOpenAircraft = {},
                            )
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun nodes(text: String): List<SemanticsNode> = composeRule
        .onAllNodes(hasText(text), useUnmergedTree = true)
        .fetchSemanticsNodes()

    private fun placed(text: String): SemanticsNode = nodes(text).single { it.layoutInfo.isPlaced }

    private fun timeNode(): SemanticsNode = composeRule
        .onAllNodes(
            SemanticsMatcher("estimated time") { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.startsWith("~") } == true
            },
            useUnmergedTree = true,
        )
        .fetchSemanticsNodes()
        .single()

    private fun SemanticsNode.textLayout(): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        return layouts.single()
    }

    /** Not shortened and not cut off. */
    private fun SemanticsNode.assertWhole(name: String) = withClue(name) {
        val layout = textLayout()
        layout.isLineEllipsized(0) shouldBe false
        layout.didOverflowHeight shouldBe false
        layout.getLineRight(0) shouldBeLessThanOrEqual layout.size.width.toFloat()
    }

    /** Width the row's second line gets inside a box of [width]. */
    private class Line(val boxPx: Int, val slotPx: Int, val detailsPx: Int, val separatorPx: Int)

    /** Measures the line at [WIDE], where everything fits, airline included. */
    private fun measureWide(airline: String, details: String): Line {
        width = WIDE
        composeRule.waitForIdle()
        val airlineNode = placed(airline)
        airlineNode.textLayout().isLineEllipsized(0) shouldBe false
        return Line(
            boxPx = WIDE.toPx(),
            slotPx = airlineNode.layoutInfo.parentInfo!!.width,
            detailsPx = placed(details).layoutInfo.width,
            separatorPx = placed(SEPARATOR).layoutInfo.width,
        )
    }

    /** Narrows the box until the line's slot is [slotPx] wide. */
    private fun narrowTo(line: Line, slotPx: Int) {
        val boxPx = line.boxPx - line.slotPx + slotPx
        width = with(composeRule.density) { boxPx.toDp() }
        composeRule.waitForIdle()
    }

    private fun Dp.toPx(): Int = with(composeRule.density) { roundToPx() }

    @Test
    fun `a long airline is shortened and the rest of the line stays whole`() {
        val airline = "Example Transcontinental Sample Airways of the Northern Counties"
        val details = "XLT7 · En route"
        setRow("k-l4", shortestAirline = "Exa…")
        val line = measureWide(airline, details)

        narrowTo(line, line.detailsPx + line.separatorPx + shortestAirlineWidth + 40)

        val airlineNode = placed(airline)
        val separatorNode = placed(SEPARATOR)
        val detailsNode = placed(details)
        val timeNode = timeNode()
        airlineNode.textLayout().isLineEllipsized(0) shouldBe true
        airlineNode.boundsInRoot.width shouldBeGreaterThan 0f
        airlineNode.layoutInfo.width shouldBeGreaterThan shortestAirlineWidth - 1
        separatorNode.assertWhole("separator")
        detailsNode.assertWhole("details")
        timeNode.assertWhole("time")

        val row = composeRule.onNodeWithTag(LIST).fetchSemanticsNode().boundsInRoot
        val inOrder = listOf(airlineNode, separatorNode, detailsNode, timeNode).map { it.boundsInRoot }
        inOrder.forEach {
            it.left shouldBeGreaterThan row.left - 0.5f
            it.right shouldBeLessThanOrEqual row.right + 0.5f
        }
        inOrder.zipWithNext().forEach { (before, after) ->
            before.right shouldBeLessThanOrEqual after.left + 0.5f
        }
    }

    @Test
    fun `a shortened airline leaves no gap before the separator`() {
        val airline = "Example Transcontinental Sample Airways of the Northern Counties"
        val details = "XLT7 · En route"
        setRow("k-l4", shortestAirline = "Exa…")
        val line = measureWide(airline, details)

        narrowTo(line, line.detailsPx + line.separatorPx + shortestAirlineWidth + 40)

        val airlineNode = placed(airline)
        val separatorNode = placed(SEPARATOR)
        val layout = airlineNode.textLayout()
        layout.isLineEllipsized(0) shouldBe true
        // getLineRight() reaches past the drawn "…", so measure what is shown instead
        val shown = airline.substring(0, layout.getLineEnd(0, visibleEnd = true)) + "…"
        val shownWidth = rowTextWidth(shown)
        val separatorLeft = separatorNode.boundsInRoot.left
        withClue(
            "shown=\"$shown\" shownWidth=$shownWidth airlineWidth=${airlineNode.boundsInRoot.width} " +
                "separatorLeft=$separatorLeft"
        ) {
            separatorLeft - airlineNode.boundsInRoot.left - shownWidth shouldBeLessThanOrEqual 1.5f
        }
    }

    private fun assertAirlineLeftOut(fontScale: Float) {
        val details = "XLA402 · XA402 · A21N · Approaching"
        setRow("k-l9", shortestAirline = "Exa…", fontScale = fontScale)
        val line = measureWide("Example Air", details)

        // Room for the details and separator, but only half of "Exa…"
        narrowTo(line, line.detailsPx + line.separatorPx + shortestAirlineWidth / 2)

        nodes("Example Air").shouldBeEmpty()
        nodes(SEPARATOR).shouldBeEmpty()
        val detailsNode = placed(details)
        detailsNode.assertWhole("details")
        timeNode().assertWhole("time")
        // Starts where the line above does, as a row without an airline
        val route = placed("🇩🇪 XYZ Example County International Airport")
        detailsNode.boundsInRoot.left shouldBe route.boundsInRoot.left
    }

    @Test
    fun `an airline without room for its first characters is left out`() {
        assertAirlineLeftOut(fontScale = 1f)
    }

    @Test
    fun `with larger text an airline without room for its first characters is left out too`() {
        assertAirlineLeftOut(fontScale = 1.3f)
    }

    @Test
    fun `an airline left out is not read out`() {
        val details = "XLA402 · XA402 · A21N · Approaching"
        // 4ca7b3 is a known aircraft, so the row is clickable and reads as one
        setRow("k-l9", shortestAirline = "Exa…")
        val line = measureWide("Example Air", details)

        narrowTo(line, line.detailsPx + line.separatorPx + shortestAirlineWidth / 2)

        val texts = composeRule.onNode(hasClickAction() and hasText(details)).fetchSemanticsNode()
            .config[SemanticsProperties.Text].map { it.text }
        texts shouldContain details
        texts.filter { "Example Air" in it }.shouldBeEmpty()
        texts shouldNotContain SEPARATOR
    }

    private companion object {
        const val LIST = "list"
        const val SEPARATOR = " · "
        val WIDE = 2000.dp
    }
}
