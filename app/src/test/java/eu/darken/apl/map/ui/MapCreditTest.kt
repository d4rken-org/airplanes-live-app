package eu.darken.apl.map.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.unit.dp
import eu.darken.apl.common.compose.PreviewWrapper
import io.kotest.matchers.collections.shouldContainExactly
import org.junit.Test
import testhelper.BaseComposeTest

class MapCreditTest : BaseComposeTest() {

    private val base = "<a href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap</a>"
    private val overlayA = "<a href=\"https://a.test\">A</a>"
    private val overlayB = "<a href=\"https://b.test\">B</a>"
    private val liberty = "https://tiles.openfreemap.org/styles/liberty"
    private val dark = "https://tiles.openfreemap.org/styles/dark"

    private val duration = MAP_CREDIT_DURATION.inWholeMilliseconds

    private val seenCredits = SeenMapCredits()

    private var present by mutableStateOf(true)
    private var styleKey by mutableStateOf<String?>(liberty)
    private var credits by mutableStateOf(listOf(base))
    private var enabled by mutableStateOf<Set<String>?>(emptySet())
    private var canShow by mutableStateOf(true)
    private var room by mutableStateOf(1000.dp)

    private val opened = mutableListOf<String>()

    private fun setCredit(restoration: StateRestorationTester? = null) {
        composeRule.mainClock.autoAdvance = false
        val content: @Composable () -> Unit = {
            PreviewWrapper {
                if (present) {
                    MapCredit(
                        styleKey = styleKey,
                        credits = credits,
                        enabledOverlayCredits = enabled,
                        canShow = canShow,
                        darkStyle = false,
                        room = room,
                        onOpenUrl = { opened += it },
                        seenCredits = seenCredits,
                    )
                }
            }
        }
        if (restoration != null) restoration.setContent(content) else composeRule.setContent(content)
    }

    /** Composes the state changes made so far, one frame later. */
    private fun frame() {
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeByFrame()
    }

    private fun advance(millis: Long) {
        composeRule.mainClock.advanceTimeBy(millis)
        frame()
    }

    private fun showToEnd() = advance(duration + 100)

    private fun restore(restoration: StateRestorationTester) {
        // The restore disposes and recomposes, which takes frames
        composeRule.mainClock.autoAdvance = true
        restoration.emulateSavedInstanceStateRestore()
        composeRule.mainClock.autoAdvance = false
    }

    /** As a finger coming down on the map. */
    private fun touch() {
        composeRule.runOnUiThread { seenCredits.markDisplayedSeen() }
        frame()
    }

    private fun assertShown(text: String) {
        composeRule.onNodeWithText(text).assertExists()
    }

    private fun assertHidden() {
        composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)).assertCountEquals(0)
    }

    private fun overlays(vararg credits: String) {
        enabled = credits.toSet()
        this.credits = listOf(base) + credits
        frame()
    }

    @Test
    fun `fresh credits show and hide after their time`() {
        setCredit()
        assertShown("OpenStreetMap")

        advance(duration - 1000)
        assertShown("OpenStreetMap")

        advance(1100)
        assertHidden()
    }

    @Test
    fun `an added credit shows again, a removed one doesn't`() {
        enabled = setOf(overlayA)
        credits = listOf(base, overlayA)
        setCredit()
        showToEnd()
        assertHidden()

        overlays(overlayA, overlayB)
        assertShown("B")
        showToEnd()
        assertHidden()

        overlays(overlayB)
        assertHidden()
        credits = listOf(base)
        frame()
        assertHidden()
    }

    @Test
    fun `an overlay switched on after the base was seen shows its credit alone`() {
        setCredit()
        showToEnd()
        assertHidden()

        overlays(overlayA)
        assertShown("A")
        composeRule.onAllNodes(hasText("OpenStreetMap", substring = true)).assertCountEquals(0)
    }

    @Test
    fun `an overlay switched off and on again shows again`() {
        enabled = setOf(overlayA)
        credits = listOf(base, overlayA)
        setCredit()
        showToEnd()

        overlays()
        assertHidden()

        overlays(overlayA)
        assertShown("A")
    }

    @Test
    fun `an overlay leaving the view model before the map doesn't show its credit`() {
        enabled = setOf(overlayA)
        credits = listOf(base, overlayA)
        setCredit()
        showToEnd()
        assertHidden()

        enabled = emptySet()
        frame()
        assertHidden()

        credits = listOf(base)
        frame()
        assertHidden()
    }

    @Test
    fun `an overlay hidden from the map while still enabled doesn't show again`() {
        enabled = setOf(overlayA)
        credits = listOf(base, overlayA)
        setCredit()
        showToEnd()
        assertHidden()

        credits = listOf(base)
        frame()
        assertHidden()

        credits = listOf(base, overlayA)
        frame()
        assertHidden()
        advance(1000)
        assertHidden()
    }

    @Test
    fun `an overlay switched off and on while hidden from the map shows again once back`() {
        enabled = setOf(overlayA)
        credits = listOf(base, overlayA)
        setCredit()
        showToEnd()
        assertHidden()

        // As in the history, which leaves the overlays off the map
        credits = listOf(base)
        frame()
        enabled = emptySet()
        frame()
        enabled = setOf(overlayA)
        frame()
        assertHidden()

        credits = listOf(base, overlayA)
        frame()
        assertShown("A")
    }

    @Test
    fun `swapping overlays shows the new one, bringing the old one back shows it again`() {
        enabled = setOf(overlayA)
        credits = listOf(base, overlayA)
        setCredit()
        showToEnd()
        assertHidden()

        overlays(overlayB)
        assertShown("B")
        showToEnd()
        assertHidden()

        overlays(overlayA, overlayB)
        assertShown("A")
    }

    @Test
    fun `a credit joining a showing restarts its time`() {
        setCredit()
        advance(duration - 1000)

        overlays(overlayA)
        assertShown("OpenStreetMap · A")
        advance(duration - 1000)
        assertShown("OpenStreetMap · A")
        advance(1100)
        assertHidden()
    }

    @Test
    fun `reloading the same style doesn't show again`() {
        setCredit()
        showToEnd()

        styleKey = null
        credits = emptyList()
        frame()
        assertHidden()

        styleKey = liberty
        credits = listOf(base)
        frame()
        assertHidden()
        advance(1000)
        assertHidden()
    }

    @Test
    fun `a new style doesn't show seen credits again`() {
        setCredit()
        showToEnd()

        styleKey = dark
        frame()
        assertHidden()
        advance(1000)
        assertHidden()
    }

    @Test
    fun `a new style loading doesn't forget seen credits`() {
        setCredit()
        showToEnd()

        styleKey = null
        credits = emptyList()
        frame()
        assertHidden()

        styleKey = dark
        credits = listOf(base)
        frame()
        assertHidden()
        advance(1000)
        assertHidden()
    }

    @Test
    fun `a credit that can't show gets its full time once it can`() {
        canShow = false
        setCredit()
        assertHidden()
        advance(duration + 1000)
        assertHidden()

        canShow = true
        frame()
        assertShown("OpenStreetMap")
        advance(3000)

        canShow = false
        frame()
        assertHidden()
        advance(3000)

        canShow = true
        frame()
        assertShown("OpenStreetMap")
        advance(duration - 1000)
        assertShown("OpenStreetMap")
        advance(1100)
        assertHidden()
    }

    @Test
    fun `a new style during a showing restarts its time`() {
        setCredit()
        advance(duration - 1000)

        styleKey = dark
        frame()
        advance(duration - 1000)
        assertShown("OpenStreetMap")
        advance(1100)
        assertHidden()
    }

    @Test
    fun `a touch hides the credit`() {
        setCredit()
        advance(1000)
        assertShown("OpenStreetMap")

        touch()
        assertHidden()
        advance(duration)
        assertHidden()
    }

    @Test
    fun `a touch while nothing shows doesn't affect a later showing`() {
        canShow = false
        setCredit()
        assertHidden()

        touch()
        canShow = true
        frame()
        assertShown("OpenStreetMap")
        advance(duration - 1000)
        assertShown("OpenStreetMap")
        advance(1100)
        assertHidden()
    }

    @Test
    fun `a new composition with the same seen credits doesn't show again`() {
        setCredit()
        showToEnd()

        present = false
        frame()
        present = true
        frame()
        assertHidden()
        advance(1000)
        assertHidden()
    }

    @Test
    fun `shown credits stay hidden after a restore`() {
        val restoration = StateRestorationTester(composeRule)
        setCredit(restoration)
        showToEnd()

        restore(restoration)
        frame()
        assertHidden()
    }

    @Test
    fun `an unfinished showing gets its full time after a restore`() {
        val restoration = StateRestorationTester(composeRule)
        setCredit(restoration)
        advance(duration - 1000)

        restore(restoration)
        frame()
        assertShown("OpenStreetMap")
        advance(duration - 1000)
        assertShown("OpenStreetMap")
        advance(1100)
        assertHidden()
    }

    @Test
    fun `a new credit after a restore shows`() {
        val restoration = StateRestorationTester(composeRule)
        setCredit(restoration)
        showToEnd()

        restore(restoration)
        frame()
        assertHidden()

        overlays(overlayA)
        assertShown("A")
    }

    @Test
    fun `a link opens its URL`() {
        credits = listOf(base, overlayA)
        setCredit()

        val text = composeRule.onNodeWithText("OpenStreetMap · A").fetchSemanticsNode()
            .config[SemanticsProperties.Text].single()
        val link = text.getLinkAnnotations(0, text.length)
            .map { it.item }
            .filterIsInstance<LinkAnnotation.Url>()
            .single { it.url == "https://a.test" }
        composeRule.runOnUiThread { link.linkInteractionListener!!.onClick(link) }

        opened shouldContainExactly listOf("https://a.test")
    }

    @Test
    fun `a credit taller than its room waits until it fits`() {
        room = 1.dp
        setCredit()
        assertHidden()
        advance(duration + 1000)
        assertHidden()

        room = 1000.dp
        frame()
        assertShown("OpenStreetMap")
        advance(duration - 1000)
        assertShown("OpenStreetMap")
        advance(1100)
        assertHidden()
    }
}
