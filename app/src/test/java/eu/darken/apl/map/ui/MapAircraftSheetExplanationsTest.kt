package eu.darken.apl.map.ui

import android.content.Context
import androidx.activity.ComponentDialog
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import eu.darken.apl.R
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.common.flight.RouteProgress
import eu.darken.apl.main.core.aircraft.AircraftSourceType
import eu.darken.apl.map.core.MapAircraftDetails
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.robolectric.shadows.ShadowDialog
import testhelper.BaseComposeTest

class MapAircraftSheetExplanationsTest : BaseComposeTest() {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val details = MapAircraftDetails(
        hex = "4CA2B1",
        callsign = "RYR1234",
        altitudeFt = 35_000,
        vertRateFpm = 1_200,
        squawk = "1000",
        position = "51.5000, -0.1000",
        sourceType = AircraftSourceType.ADSB,
        source = "adsb_icao",
    )

    private val squawkSearches = mutableListOf<String>()
    private val copies = mutableListOf<String>()

    private fun setSheet(
        details: () -> MapAircraftDetails = { this.details },
        squawkLinks: Boolean = true,
        copying: Boolean = true,
        scrollToDetails: Boolean = true,
    ) {
        composeRule.setContent {
            PreviewWrapper {
                AircraftDetailsSheetContent(
                    details = details(),
                    loading = false,
                    note = null,
                    route = null,
                    routeProgress = RouteProgress.Unknown,
                    progress = { 0f },
                    topInset = 0,
                    maxPhotoHeight = 0,
                    onCollapsedHeight = {},
                    watched = false,
                    seen = null,
                    serverNow = { 0L },
                    placeAction = AircraftPlaceAction.ShowOnMap(onClick = {}),
                    onWatchAction = {},
                    onWatchWithNote = {},
                    onPhotoClick = {},
                    onSquawkClick = if (squawkLinks) { squawk -> squawkSearches.add(squawk) } else null,
                    onCopy = if (copying) { text -> copies.add(text) } else null,
                )
            }
        }
        // The details are the list's second item, below the glance
        if (scrollToDetails) composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(1)
    }

    private fun setSheet(details: MapAircraftDetails, squawkLinks: Boolean = true) =
        setSheet(details = { details }, squawkLinks = squawkLinks)

    private fun clickLabel(label: String) = SemanticsMatcher("click label is '$label'") {
        it.config.getOrNull(SemanticsActions.OnClick)?.label == label
    }

    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
    private val inDialog = hasAnyAncestor(isDialog())
    private val inDialogScroll = hasAnyAncestor(hasScrollAction() and hasAnyAncestor(isDialog()))

    private val squawkLabel get() = context.getString(R.string.common_squawk_label)
    private val sourceLabel get() = context.getString(R.string.map_aircraft_details_source_label)
    private val explainSquawk get() = context.getString(R.string.map_aircraft_details_explain_squawk)
    private val explainSource get() = context.getString(R.string.map_aircraft_details_explain_source)
    private val squawkIntro get() = context.getString(R.string.aircraft_squawk_explanation_intro)
    private val sourceIntro get() = context.getString(R.string.aircraft_source_explanation_intro)

    private fun string(id: Int) = context.getString(id)

    private fun node(text: String) = composeRule.onNode(hasText(text), useUnmergedTree = true).performScrollTo()

    /** The label with its icon, one button outside of the dialog. */
    private fun explainButton(label: String): SemanticsNodeInteraction =
        composeRule.onNode(hasText(label) and hasClickAction() and inDialog.not()).performScrollTo()

    /** The texts of the dialog's scrolling body, in order. */
    private fun dialogBody(): List<String> = composeRule
        .onAllNodes(inDialogScroll and SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
        .fetchSemanticsNodes()
        .map { node -> node.config[SemanticsProperties.Text].joinToString("") { it.text } }

    private fun assertNoDialog() = composeRule.onNode(isDialog()).assertDoesNotExist()

    private fun closeDialog() {
        composeRule.onNode(hasText(string(R.string.common_close_action)) and inDialog).performClick()
        assertNoDialog()
    }

    private fun pressBack() {
        composeRule.runOnIdle {
            (ShadowDialog.getLatestDialog() as ComponentDialog).onBackPressedDispatcher.onBackPressed()
        }
    }

    @Test
    fun `squawk and source labels explain themselves, squawk lists every regional meaning in order`() {
        setSheet()

        explainButton(sourceLabel).assert(clickLabel(explainSource)).assert(isButton)
        explainButton(squawkLabel)
            .assert(clickLabel(explainSquawk))
            .assert(isButton)
            .performClick()

        composeRule.onNode(hasText(squawkLabel) and inDialog).assertExists()
        dialogBody() shouldBe listOf(
            squawkIntro,
            "1000 (Europe): " + string(R.string.aircraft_squawk_meaning_1000_europe),
            "1000 (United States): " + string(R.string.aircraft_squawk_meaning_1000_us),
            "1000 (Canada): " + string(R.string.aircraft_squawk_meaning_1000_canada),
        )

        closeDialog()
    }

    @Test
    fun `an unlisted squawk has no known meaning`() {
        setSheet(details.copy(squawk = "1234"))

        explainButton(squawkLabel).performClick()

        dialogBody() shouldBe listOf(squawkIntro, "1234: " + string(R.string.aircraft_squawk_meaning_unknown))
    }

    @Test
    fun `a worldwide squawk names no region`() {
        setSheet(details.copy(squawk = "7700"))

        explainButton(squawkLabel).performClick()

        dialogBody() shouldBe listOf(squawkIntro, "7700: " + string(R.string.aircraft_squawk_meaning_7700))
    }

    @Test
    fun `the source explains ADS-B`() {
        setSheet()

        explainButton(sourceLabel).performClick()

        composeRule.onNode(hasText(sourceLabel) and inDialog).assertExists()
        dialogBody() shouldBe listOf(sourceIntro, "ADS-B: " + string(R.string.aircraft_source_meaning_adsb))
    }

    @Test
    fun `a TIS-B source shows as other and its explanation names TIS-B`() {
        setSheet(details.copy(sourceType = AircraftSourceType.OTHER, source = "tisb_icao"))

        node(string(R.string.aircraft_source_other_label)).assertExists()
        explainButton(sourceLabel).performClick()

        dialogBody() shouldBe listOf(sourceIntro, "TIS-B: " + string(R.string.aircraft_source_meaning_tisb))
    }

    @Test
    fun `the meaning scrolls inside the dialog`() {
        setSheet()

        explainButton(squawkLabel).performClick()

        composeRule.onNode(
            hasText("1000 (Canada): " + string(R.string.aircraft_squawk_meaning_1000_canada)) and inDialogScroll,
            useUnmergedTree = true,
        ).assertExists()
    }

    @Test
    fun `labels without an explanation offer no click`() {
        setSheet()

        // Not unique, the glance may label its altitude too
        composeRule.onAllNodes(hasText(string(R.string.common_altitude_label)), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .shouldNotBeEmpty()
        node(string(R.string.map_aircraft_details_callsign_label)).assertExists()
        composeRule.onAllNodes(hasText(string(R.string.common_altitude_label)) and hasClickAction()).assertCountEquals(0)
        composeRule.onAllNodes(hasText(string(R.string.map_aircraft_details_callsign_label)) and hasClickAction())
            .assertCountEquals(0)
    }

    @Test
    fun `a source type without a raw source offers no explanation`() {
        setSheet(details.copy(source = null))

        node(sourceLabel).assertExists()
        composeRule.onAllNodes(hasText(sourceLabel) and hasClickAction()).assertCountEquals(0)
        composeRule.onAllNodes(clickLabel(explainSource)).assertCountEquals(0)
    }

    @Test
    fun `a blank raw source offers no explanation`() {
        setSheet(details.copy(sourceType = AircraftSourceType.OTHER, source = "  "))

        node(sourceLabel).assertExists()
        composeRule.onAllNodes(hasText(sourceLabel) and hasClickAction()).assertCountEquals(0)
        composeRule.onAllNodes(clickLabel(explainSource)).assertCountEquals(0)
    }

    @Test
    fun `the label, its icon and the space just above it explain, the value searches`() {
        setSheet()

        explainButton(squawkLabel).performClick()
        closeDialog()

        explainButton(squawkLabel).performTouchInput { click(Offset(width - 7.dp.toPx(), centerY)) }
        closeDialog()

        // Outside the drawn label, within its minimum touch target
        explainButton(squawkLabel).performTouchInput { click(Offset(centerX, -4.dp.toPx())) }
        closeDialog()

        squawkSearches.shouldBeEmpty()
        copies.shouldBeEmpty()

        node("1000").performClick()

        squawkSearches shouldBe listOf("1000")
        copies.shouldBeEmpty()
        assertNoDialog()
    }

    @Test
    fun `the squawk value searches for the trimmed code`() {
        setSheet(details.copy(squawk = " 7700 "))

        composeRule.onNode(clickLabel(context.getString(R.string.map_aircraft_details_search_squawk_x, "7700")))
            .performScrollTo()
            .assert(isButton)
            .performClick()

        squawkSearches shouldBe listOf("7700")
    }

    @Test
    fun `the squawk value keeps leading zeros and copies on a long press`() {
        setSheet(details.copy(squawk = "0021"))

        node("0021")
            .assert(clickLabel(context.getString(R.string.map_aircraft_details_search_squawk_x, "0021")))
            .performClick()
            .performTouchInput { longClick() }

        squawkSearches shouldBe listOf("0021")
        copies shouldBe listOf("0021")
    }

    @Test
    fun `the squawk value is plain text without squawk links`() {
        setSheet(details, squawkLinks = false)

        node("1000").assert(hasClickAction().not())
    }

    @Test
    fun `a blank squawk offers neither an explanation nor a link`() {
        setSheet(details.copy(squawk = "  "))

        node(squawkLabel).assertExists()
        composeRule.onAllNodes(clickLabel(explainSquawk)).assertCountEquals(0)
        composeRule.onAllNodes(
            SemanticsMatcher("searches for a squawk") {
                val prefix = context.getString(R.string.map_aircraft_details_search_squawk_x, "")
                it.config.getOrNull(SemanticsActions.OnClick)?.label?.startsWith(prefix) == true
            },
        ).assertCountEquals(0)
    }

    @Test
    fun `the open dialog follows the aircraft's values`() {
        val current = mutableStateOf(details)
        setSheet(details = { current.value })

        explainButton(squawkLabel).performClick()
        current.value = current.value.copy(squawk = "7700")
        dialogBody() shouldBe listOf(squawkIntro, "7700: " + string(R.string.aircraft_squawk_meaning_7700))
        closeDialog()

        current.value = current.value.copy(sourceType = AircraftSourceType.OTHER, source = "tisb_icao")
        explainButton(sourceLabel).performClick()
        dialogBody() shouldBe listOf(sourceIntro, "TIS-B: " + string(R.string.aircraft_source_meaning_tisb))
        current.value = current.value.copy(source = "adsr_icao")
        dialogBody() shouldBe listOf(sourceIntro, "ADS-R: " + string(R.string.aircraft_source_meaning_adsr))
        closeDialog()
    }

    @Test
    fun `another aircraft closes the dialog`() {
        val current = mutableStateOf(details)
        setSheet(details = { current.value })

        explainButton(squawkLabel).performClick()
        composeRule.onNode(isDialog()).assertExists()

        current.value = current.value.copy(hex = "3C6444")

        assertNoDialog()
    }

    @Test
    fun `a squawk that goes away closes the dialog for good`() {
        val current = mutableStateOf(details)
        setSheet(details = { current.value })

        explainButton(squawkLabel).performClick()
        composeRule.onNode(isDialog()).assertExists()

        current.value = current.value.copy(squawk = null)
        assertNoDialog()

        current.value = current.value.copy(squawk = "1000")
        composeRule.waitForIdle()
        assertNoDialog()
    }

    @Test
    fun `a source that goes blank closes the dialog for good`() {
        val current = mutableStateOf(details)
        setSheet(details = { current.value })

        explainButton(sourceLabel).performClick()
        composeRule.onNode(isDialog()).assertExists()

        current.value = current.value.copy(source = "")
        assertNoDialog()
        composeRule.onAllNodes(hasText(sourceLabel) and hasClickAction()).assertCountEquals(0)
        composeRule.onAllNodes(clickLabel(explainSource)).assertCountEquals(0)

        current.value = current.value.copy(source = "  ")
        assertNoDialog()
        composeRule.onAllNodes(hasText(sourceLabel) and hasClickAction()).assertCountEquals(0)
        composeRule.onAllNodes(clickLabel(explainSource)).assertCountEquals(0)

        current.value = current.value.copy(source = "adsb_icao")
        composeRule.waitForIdle()
        assertNoDialog()
    }

    @Test
    fun `back closes the dialog`() {
        setSheet()

        explainButton(squawkLabel).performClick()
        composeRule.onNode(isDialog()).assertExists()

        pressBack()

        assertNoDialog()
    }

    @Test
    fun `the emergency banner explains the squawk`() {
        setSheet(details = { details.copy(squawk = "7700") }, scrollToDetails = false)

        val banner = context.getString(
            R.string.map_aircraft_details_squawk_banner,
            "7700",
            string(R.string.map_aircraft_details_squawk_7700),
        )
        composeRule.onNode(hasText(banner))
            .assert(clickLabel(explainSquawk))
            .assert(isButton)
            .performClick()

        dialogBody() shouldBe listOf(squawkIntro, "7700: " + string(R.string.aircraft_squawk_meaning_7700))
    }
}
