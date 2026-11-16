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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.darken.apl.airport.core.AirportFlightList
import eu.darken.apl.airport.core.airportFlightListFixture
import eu.darken.apl.airport.core.airportFlightsFixture
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.server.api.AirportFlightAirlineDto
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeLessThanOrEqual
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import testhelper.BaseComposeTest
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

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

    /** Lays a text out in the row's style and font scale, shortened with "…" to the given width. */
    private var rowTextFit: (String, Int) -> TextLayoutResult = { _, _ -> error("setRow() first") }

    private fun setRow(
        key: String,
        shortestAirline: String,
        fontScale: Float = 1f,
        list: AirportFlightList = airportFlightListFixture("answered-airlines.json"),
    ) {
        val display = AirportFlightsDisplay.from(
            list = list,
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
                    rowTextFit = { text, maxWidth ->
                        measurer.measure(
                            text = text,
                            style = style,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            constraints = Constraints(maxWidth = maxWidth),
                        )
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

    /** Unlike [SemanticsNode.boundsInRoot], not clipped where the box is wider than the window. */
    private val SemanticsNode.left: Float get() = positionInRoot.x

    /** What [layout] of [airline] draws: its kept start and "…", or the whole name. */
    private fun drawn(airline: String, layout: TextLayoutResult): String = if (layout.isLineEllipsized(0)) {
        airline.substring(0, layout.getLineEnd(0, visibleEnd = true)) + "…"
    } else {
        airline
    }

    /** The fixture with row [key]'s airline renamed to [airline]. */
    private fun withAirline(key: String, airline: String): AirportFlightList {
        val base = airportFlightsFixture("answered-airlines.json")
        val entry = base.entries.single { it.key == key }
        val renamed = entry.copy(flight = entry.flight!!.copy(airline = AirportFlightAirlineDto(name = airline)))
        return AirportFlightList.from(base.copy(entries = base.entries.map { if (it == entry) renamed else it }))!!
    }

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
        detailsNode.left shouldBe route.left
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

    @Test
    fun `a shortened airline keeps every character it measured room for`() {
        val airline = "Example Transcontinental Sample Airways of the Northern Counties"
        val details = "XLT7 · En route"
        setRow("k-l4", shortestAirline = "Exa…")
        val line = measureWide(airline, details)

        // Room for the airline where the ellipsizer keeps fewer characters inside the width of "prefix…" it kept
        val mismatch = (shortestAirlineWidth until line.slotPx).firstNotNullOfOrNull { remaining ->
            val fitted = rowTextFit(airline, remaining)
            if (!fitted.isLineEllipsized(0)) return@firstNotNullOfOrNull null
            val kept = fitted.getLineEnd(0, visibleEnd = true)
            val refitted = rowTextFit(airline, rowTextWidth(airline.substring(0, kept) + "…"))
            if (refitted.getLineEnd(0, visibleEnd = true) < kept) remaining to kept else null
        }
        val (remaining, kept) = withClue("no width where the ellipsizer needs more than \"prefix…\" measures") {
            mismatch.shouldNotBeNull()
        }

        narrowTo(line, line.detailsPx + line.separatorPx + remaining)

        val airlineNode = placed(airline)
        val layout = airlineNode.textLayout()
        val shown = drawn(airline, layout)
        val gap = placed(SEPARATOR).left - airlineNode.left - rowTextWidth(shown)
        withClue("remaining=$remaining kept=$kept shown=\"$shown\" gap=$gap") {
            layout.getLineEnd(0, visibleEnd = true) shouldBe kept
            abs(gap) shouldBeLessThanOrEqual 1.5f
        }
    }

    /**
     * Steps the line one pixel at a time from room for only the details and separator to room for the airline's
     * first [AIRLINE_MIN_CHARS] characters, "…" and 40px more. At each width the airline is left out with its
     * separator, or keeps those characters (or its whole name) with the separator right after it.
     */
    private fun assertEveryWidthKeepsTheAirlineReadable(
        key: String,
        airline: String,
        details: String,
        fontScale: Float,
        list: AirportFlightList = airportFlightListFixture("answered-airlines.json"),
    ) {
        val shortestAirline = airline.substring(0, airline.offsetByCodePoints(0, AIRLINE_MIN_CHARS)) + "…"
        setRow(key, shortestAirline = shortestAirline, fontScale = fontScale, list = list)
        val line = measureWide(airline, details)
        val narrowest = line.detailsPx + line.separatorPx

        val failures = (narrowest..narrowest + shortestAirlineWidth + 40).mapNotNull { slotPx ->
            narrowTo(line, slotPx)
            val airlineNodes = nodes(airline)
            val separatorNodes = nodes(SEPARATOR)
            if (airlineNodes.isEmpty() && separatorNodes.isEmpty()) return@mapNotNull null
            if (airlineNodes.size != 1 || separatorNodes.size != 1) {
                return@mapNotNull "slot=$slotPx: ${airlineNodes.size} airline, ${separatorNodes.size} separator nodes"
            }
            val airlineNode = airlineNodes.single()
            val layout = airlineNode.textLayout()
            val keptChars = airline.codePointCount(0, layout.getLineEnd(0, visibleEnd = true))
            val shown = drawn(airline, layout)
            val afterBox = separatorNodes.single().left - airlineNode.left - airlineNode.layoutInfo.width
            when {
                layout.isLineEllipsized(0) && keptChars < AIRLINE_MIN_CHARS -> "slot=$slotPx: shows \"$shown\""
                abs(afterBox) > 1.5f -> "slot=$slotPx: shows \"$shown\", separator ${afterBox}px after its box"
                else -> null
            }
        }
        failures.shouldBeEmpty()
    }

    @Test
    fun `at every width a short airline keeps its first characters or is left out`() {
        assertEveryWidthKeepsTheAirlineReadable(
            key = "k-l9",
            airline = "Example Air",
            details = "XLA402 · XA402 · A21N · Approaching",
            fontScale = 1f,
        )
    }

    @Test
    fun `with larger text at every width a short airline keeps its first characters or is left out`() {
        assertEveryWidthKeepsTheAirlineReadable(
            key = "k-l9",
            airline = "Example Air",
            details = "XLA402 · XA402 · A21N · Approaching",
            fontScale = 1.3f,
        )
    }

    @Test
    fun `at every width a long airline keeps its first characters or is left out`() {
        assertEveryWidthKeepsTheAirlineReadable(
            key = "k-l4",
            airline = "Example Transcontinental Sample Airways of the Northern Counties",
            details = "XLT7 · En route",
            fontScale = 1f,
        )
    }

    @Test
    @Config(qualifiers = "420dpi")
    fun `on a dense screen with larger text at every width an airline keeps its first characters or is left out`() {
        // Here the ellipsizer keeps only "VA…" at some widths that "VAT…" measures to fit
        val airline = "VAT Sample Air"
        assertEveryWidthKeepsTheAirlineReadable(
            key = "k-l9",
            airline = airline,
            details = "XLA402 · XA402 · A21N · Approaching",
            fontScale = 1.3f,
            list = withAirline("k-l9", airline),
        )
    }

    @Test
    fun `a short airline that fits whole is shown whole`() {
        val airline = "Zili"
        val details = "XLA401 · A320 · Approaching"
        setRow("k-l1", shortestAirline = "Zil…", list = withAirline("k-l1", airline))
        // The whole name is narrower than its first three characters and "…"
        rowTextWidth(airline) shouldBeLessThan shortestAirlineWidth
        val line = measureWide(airline, details)

        narrowTo(line, line.detailsPx + line.separatorPx + rowTextWidth(airline))

        nodes(airline) shouldHaveSize 1
        val airlineNode = placed(airline)
        airlineNode.assertWhole("airline")
        abs(placed(SEPARATOR).left - airlineNode.left - rowTextWidth(airline)) shouldBeLessThanOrEqual 1.5f
    }

    @Test
    fun `a row without a known aircraft reads its airline and details as one`() {
        val airline = "Example Transcontinental Sample Airways of the Northern Counties"
        val details = "XLT7 · En route"
        // k-l4 has no known aircraft, so its row isn't clickable
        setRow("k-l4", shortestAirline = "Exa…")
        measureWide(airline, details)

        val merged = composeRule.onAllNodes(hasText(airline) and hasText(details)).fetchSemanticsNodes()
        merged shouldHaveSize 1
        merged.single().config[SemanticsProperties.Text].map { it.text } shouldContain SEPARATOR
    }

    @Test
    fun `a known aircraft's row reads its airline and details as one button`() {
        val airline = "Example Air"
        val details = "XLA401 · A320 · Approaching"
        // 3c6444 is a known aircraft, so the row is clickable
        setRow("k-l1", shortestAirline = "Exa…")
        measureWide(airline, details)

        val button = composeRule
            .onAllNodes(hasClickAction() and hasText(airline) and hasText(details))
            .fetchSemanticsNodes()
        button shouldHaveSize 1
        button.single().config[SemanticsProperties.Text].map { it.text } shouldContain SEPARATOR
        composeRule.onAllNodes(hasText(airline)).fetchSemanticsNodes() shouldHaveSize 1
    }

    private companion object {
        const val LIST = "list"
        const val SEPARATOR = " · "
        const val AIRLINE_MIN_CHARS = 3
        val WIDE = 2000.dp
    }
}
