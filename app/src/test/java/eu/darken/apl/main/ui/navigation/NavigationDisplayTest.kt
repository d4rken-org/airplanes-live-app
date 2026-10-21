package eu.darken.apl.main.ui.navigation

import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedDispatcher
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import eu.darken.apl.common.compose.BottomNavSceneStrategy
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.common.navigation.LocalCoveredBySheet
import eu.darken.apl.common.navigation.LocalNavigationController
import eu.darken.apl.common.navigation.NavigationController
import eu.darken.apl.common.navigation.NavigationDisplay
import eu.darken.apl.common.navigation.NavigationState
import eu.darken.apl.common.navigation.OverlaySceneStrategy
import eu.darken.apl.common.navigation.rememberNavigationState
import eu.darken.apl.main.ui.settings.DestinationSettingsIndex
import eu.darken.apl.map.core.MapOptions
import eu.darken.apl.map.ui.DestinationMap
import eu.darken.apl.map.ui.MapRequests
import eu.darken.apl.search.ui.DestinationSearch
import eu.darken.apl.search.ui.SearchRequests
import eu.darken.apl.search.ui.actions.DestinationSearchAction
import eu.darken.apl.watch.ui.DestinationCreateAircraftWatch
import eu.darken.apl.watch.ui.DestinationWatchDetails
import eu.darken.apl.watch.ui.DestinationWatchList
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Test
import testhelper.BaseComposeTest
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalMaterial3Api::class)
class NavigationDisplayTest : BaseComposeTest() {

    private val setup = MainTabSetup(MapRequests(), SearchRequests())
    private val controller = NavigationController()
    private lateinit var state: NavigationState
    private lateinit var backDispatcher: OnBackPressedDispatcher

    class Probe : ViewModel() {
        val id = ids.incrementAndGet()
    }

    private fun setDisplay(restoration: StateRestorationTester? = null) {
        if (restoration != null) restoration.setContent { Display() } else composeRule.setContent { Display() }
    }

    @Composable
    private fun Display() {
        // As MainActivity does
        val activity = LocalContext.current as ComponentActivity
        backDispatcher = activity.onBackPressedDispatcher
        CompositionLocalProvider(
            LocalNavigationEventDispatcherOwner provides activity,
            LocalNavigationController provides controller,
        ) {
            PreviewWrapper {
                state = rememberNavigationState(setup)
                remember(state) { controller.setup(state) }
                NavigationDisplay(
                    state = state,
                    sceneStrategy = remember {
                        OverlaySceneStrategy()
                            .then(
                                BottomNavSceneStrategy { tab ->
                                    Box(Modifier.testTag(BAR)) { BottomNavBar(selectedTab = tab) }
                                }
                            )
                            .then(SinglePaneSceneStrategy())
                    },
                    entryProvider = { key -> probeEntry(key) },
                    onBack = { controller.up() },
                )
            }
        }
    }

    private fun probeEntry(key: NavKey): NavEntry<NavKey> {
        val isSheet = key is DestinationWatchDetails || key is DestinationSearchAction
        val isDialog = key is DestinationCreateAircraftWatch
        val metadata = when {
            isSheet -> OverlaySceneStrategy.sheet()
            isDialog -> OverlaySceneStrategy.dialog()
            else -> emptyMap()
        }
        return NavEntry(key, metadata = metadata) {
            when {
                isSheet -> ModalBottomSheet(onDismissRequest = { controller.up() }) { ProbeContent(key) }
                isDialog -> Dialog(onDismissRequest = { controller.up() }) { ProbeContent(key) }
                else -> ProbeContent(key)
            }
        }
    }

    @Composable
    private fun ProbeContent(key: NavKey) {
        val vm: Probe = viewModel()
        var count by rememberSaveable { mutableIntStateOf(0) }
        Column {
            Text(
                "${key::class.simpleName} vm=${vm.id} covered=${LocalCoveredBySheet.current} count=$count",
                Modifier.testTag(PROBE),
            )
            Button(onClick = { count++ }) { Text("count ${key::class.simpleName}") }
        }
    }

    /** The probe on top, a sheet's probe when one is open over its tab. */
    private fun shown(): String = composeRule.onAllNodesWithTag(PROBE).fetchSemanticsNodes()
        .last().config[SemanticsProperties.Text].joinToString()

    private fun shownAll(): List<String> = composeRule.onAllNodesWithTag(PROBE).fetchSemanticsNodes()
        .map { it.config[SemanticsProperties.Text].joinToString() }

    private fun count(key: NavKey) = composeRule.onNodeWithText("count ${key::class.simpleName}").performClick()

    private fun navigate(block: NavigationController.() -> Unit) {
        composeRule.runOnIdle { controller.block() }
        composeRule.waitForIdle()
    }

    @Test
    fun `a tab comes back with its ViewModel and saved state`() {
        setDisplay()
        navigate { enterTabs() }
        count(DestinationMap())
        count(DestinationMap())
        val onMap = shown()

        navigate { selectTab(MainTab.SEARCH) }
        shown() shouldNotBe onMap
        navigate { selectTab(MainTab.MAP) }

        shown() shouldBe onMap
        onMap.endsWith("count=2") shouldBe true
    }

    @Test
    fun `a tab that was not drawn at all comes back with its state`() {
        setDisplay()
        navigate { enterTabs() }
        navigate { selectTab(MainTab.SEARCH) }
        count(DestinationSearch())
        val onSearch = shown()

        // Search is neither shown nor what back leads to here
        navigate { selectTab(MainTab.WATCH) }
        navigate { selectTab(MainTab.SEARCH) }

        shown() shouldBe onSearch
        onSearch.endsWith("count=1") shouldBe true
    }

    @Test
    fun `the same screen open in two tabs has two ViewModels`() {
        setDisplay()
        navigate { enterTabs() }
        navigate { goTo(DestinationSearchAction(hex = "3c6589")) }
        val fromMap = shown()

        navigate { selectTab(MainTab.SEARCH) }
        navigate { goTo(DestinationSearchAction(hex = "3c6589")) }

        shown() shouldNotBe fromMap
        navigate { selectTab(MainTab.MAP) }
        shown() shouldBe fromMap
    }

    @Test
    fun `back from a map opened by another tab returns to that tab's open screen`() {
        setDisplay()
        navigate { enterTabs() }
        navigate { selectTab(MainTab.WATCH) }
        navigate { goTo(DestinationWatchDetails(watchId = "w1")) }
        count(DestinationWatchDetails(watchId = "w1"))
        val details = shown()
        details.startsWith("DestinationWatchDetails") shouldBe true

        navigate { goTo(DestinationMap(mapOptions = MapOptions.focus("3C6589"))) }
        shown().startsWith("DestinationMap") shouldBe true

        composeRule.runOnIdle { backDispatcher.onBackPressed() }
        composeRule.waitForIdle()

        shown() shouldBe details
        state.currentTab shouldBe MainTab.WATCH
    }

    @Test
    fun `tabs, their screens and where back goes survive a restore`() {
        val restoration = StateRestorationTester(composeRule)
        setDisplay(restoration)
        navigate { enterTabs() }
        navigate { selectTab(MainTab.SEARCH) }
        navigate { goTo(DestinationSearchAction(hex = "3c6589")) }
        navigate { selectTab(MainTab.WATCH) }
        navigate { goTo(DestinationWatchDetails(watchId = "w1")) }
        navigate { goTo(DestinationMap(mapOptions = MapOptions.focus("3C6589"))) }

        restoration.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()

        shown().startsWith("DestinationMap") shouldBe true
        state.inTabs shouldBe true
        state.currentTab shouldBe MainTab.MAP
        state.returnTab shouldBe MainTab.WATCH
        state.stack(MainTab.SEARCH).toList() shouldBe listOf(DestinationSearch(), DestinationSearchAction(hex = "3c6589"))
        state.stack(MainTab.WATCH).toList() shouldBe listOf(DestinationWatchList(), DestinationWatchDetails(watchId = "w1"))

        composeRule.runOnIdle { backDispatcher.onBackPressed() }
        composeRule.waitForIdle()

        state.currentTab shouldBe MainTab.WATCH
        shown().startsWith("DestinationWatchDetails") shouldBe true
    }

    @Test
    fun `the open tab and its screen survive a restore`() {
        val restoration = StateRestorationTester(composeRule)
        setDisplay(restoration)
        navigate { enterTabs() }
        navigate { selectTab(MainTab.WATCH) }
        navigate { goTo(DestinationWatchDetails(watchId = "w1")) }

        restoration.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()

        state.currentTab shouldBe MainTab.WATCH
        shown().startsWith("DestinationWatchDetails") shouldBe true
    }

    /** Back on a sheet reaches its own window, which dismisses it with one [NavigationController.up]. */
    private fun dismissSheet() = navigate { up() }

    @Test
    fun `a sheet takes the place of the sheet below it, and back shows that one again`() {
        setDisplay()
        navigate { enterTabs() }
        navigate { selectTab(MainTab.WATCH) }
        navigate { goTo(DestinationWatchDetails(watchId = "w1")) }
        val details = shown()

        navigate { goTo(DestinationSearchAction(hex = "3c6589")) }

        shown().startsWith("DestinationSearchAction") shouldBe true
        shownAll().none { it.startsWith("DestinationWatchDetails") } shouldBe true

        dismissSheet()

        shown() shouldBe details
    }

    @Test
    fun `a dialog shows over the sheet below it`() {
        setDisplay()
        navigate { enterTabs() }
        navigate { selectTab(MainTab.WATCH) }
        navigate { goTo(DestinationWatchDetails(watchId = "w1")) }

        navigate { goTo(DestinationCreateAircraftWatch(hex = "3c6589")) }

        shown().startsWith("DestinationCreateAircraftWatch") shouldBe true
        shownAll().any { it.startsWith("DestinationWatchDetails") } shouldBe true
    }

    @Test
    fun `a tab's screen knows when a sheet covers it`() {
        setDisplay()
        navigate { enterTabs() }
        shown().contains("covered=false") shouldBe true

        navigate { goTo(DestinationWatchDetails(watchId = "w1")) }

        shownAll().first { it.startsWith("DestinationMap") }.contains("covered=true") shouldBe true

        dismissSheet()

        shown().contains("covered=false") shouldBe true
    }

    @Test
    fun `a tab fading out sees its own stack, not the sheet of the tab coming in`() {
        setDisplay()
        navigate { enterTabs() }
        navigate { selectTab(MainTab.WATCH) }
        navigate { goTo(DestinationWatchDetails(watchId = "w1")) }
        navigate { selectTab(MainTab.MAP) }

        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle { controller.selectTab(MainTab.WATCH) }
        // Halfway through the tab fade, both tabs are composed
        composeRule.mainClock.advanceTimeBy(100)
        composeRule.mainClock.advanceTimeByFrame()

        state.currentTab shouldBe MainTab.WATCH
        state.stack(MainTab.WATCH).last() shouldBe DestinationWatchDetails(watchId = "w1")
        shownAll().first { it.startsWith("DestinationMap") }.contains("covered=false") shouldBe true
        composeRule.mainClock.autoAdvance = true
    }

    @Test
    fun `the bar shows on a tab's own screen, not on a screen opened over it`() {
        setDisplay()
        navigate { enterTabs() }
        navigate { selectTab(MainTab.SEARCH) }
        composeRule.onAllNodesWithTag(BAR).assertCountEquals(1)

        navigate { goTo(DestinationSettingsIndex) }

        composeRule.onAllNodesWithTag(BAR).assertCountEquals(0)
    }

    companion object {
        private const val PROBE = "probe"
        private const val BAR = "bar"
        private val ids = AtomicInteger()
    }
}
