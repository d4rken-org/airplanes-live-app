package eu.darken.apl.common.navigation

import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedDispatcher
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
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
import eu.darken.apl.map.core.MapOptions
import eu.darken.apl.map.ui.DestinationMap
import eu.darken.apl.search.ui.DestinationSearch
import eu.darken.apl.search.ui.actions.DestinationSearchAction
import eu.darken.apl.watch.ui.DestinationWatchDetails
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Test
import testhelper.BaseComposeTest
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalMaterial3Api::class)
class NavigationDisplayTest : BaseComposeTest() {

    private val controller = NavigationController(TabRequests())
    private lateinit var state: NavigationState
    private lateinit var backDispatcher: OnBackPressedDispatcher

    class Probe : ViewModel() {
        val id = ids.incrementAndGet()
    }

    private fun setDisplay() = composeRule.setContent {
        // As MainActivity does
        val activity = LocalContext.current as ComponentActivity
        backDispatcher = activity.onBackPressedDispatcher
        CompositionLocalProvider(
            LocalNavigationEventDispatcherOwner provides activity,
            LocalNavigationController provides controller,
        ) {
            PreviewWrapper {
                state = rememberNavigationState()
                remember(state) { controller.setup(state) }
                NavigationDisplay(
                    state = state,
                    sceneStrategy = remember {
                        BottomSheetSceneStrategy().then(BottomNavSceneStrategy()).then(SinglePaneSceneStrategy())
                    },
                    entryProvider = { key -> probeEntry(key) },
                    onBack = { controller.up() },
                )
            }
        }
    }

    private fun probeEntry(key: NavKey): NavEntry<NavKey> {
        val tab = (key as? NavigationDestination)?.let { MainTab.of(it) }
        val isSheet = key is DestinationWatchDetails
        val metadata = when {
            tab != null -> BottomNavSceneStrategy.bottomNavTab(tab)
            isSheet -> BottomSheetSceneStrategy.bottomSheet()
            else -> emptyMap()
        }
        return NavEntry(key, metadata = metadata) {
            if (isSheet) {
                ModalBottomSheet(onDismissRequest = { controller.up() }) { ProbeContent(key) }
            } else {
                ProbeContent(key)
            }
        }
    }

    @Composable
    private fun ProbeContent(key: NavKey) {
        val vm: Probe = viewModel()
        var count by rememberSaveable { mutableIntStateOf(0) }
        Column {
            Text("${key::class.simpleName} vm=${vm.id} count=$count", Modifier.testTag(PROBE))
            Button(onClick = { count++ }) { Text("count ${key::class.simpleName}") }
        }
    }

    /** The probe on top, a sheet's probe when one is open over its tab. */
    private fun shown(): String = composeRule.onAllNodesWithTag(PROBE).fetchSemanticsNodes()
        .last().config[SemanticsProperties.Text].joinToString()

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

    companion object {
        private const val PROBE = "probe"
        private val ids = AtomicInteger()
    }
}
