package eu.darken.apl.main.ui.navigation

import androidx.compose.runtime.mutableStateOf
import eu.darken.apl.common.navigation.NavTab
import eu.darken.apl.common.navigation.NavigationController
import eu.darken.apl.common.navigation.NavigationState
import eu.darken.apl.main.ui.DestinationMain
import eu.darken.apl.main.ui.DestinationPrivacy
import eu.darken.apl.main.ui.DestinationWelcome
import eu.darken.apl.main.ui.settings.DestinationSettingsIndex
import eu.darken.apl.map.core.MapOptions
import eu.darken.apl.map.ui.DestinationMap
import eu.darken.apl.map.ui.MapRequests
import eu.darken.apl.search.ui.DestinationSearch
import eu.darken.apl.search.ui.SearchRequests
import eu.darken.apl.search.ui.SearchTarget
import eu.darken.apl.search.ui.actions.DestinationSearchAction
import eu.darken.apl.watch.ui.DestinationWatchDetails
import eu.darken.apl.watch.ui.DestinationWatchList
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class NavigationControllerTest : BaseTest() {

    private val mapRequests = MapRequests()
    private val searchRequests = SearchRequests()
    private val setup = MainTabSetup(mapRequests, searchRequests)

    private fun controllerWith(state: NavigationState = NavigationState(setup, inTabs = mutableStateOf(true))) =
        NavigationController().apply { setup(state) } to state

    private fun NavigationState.stackOf(tab: NavTab) = stack(tab).toList()

    @Test
    fun `a saved tab id no tab has anymore starts over at the start tab`() {
        val state = NavigationState(setup, currentTabId = mutableStateOf("GONE"), returnTabId = mutableStateOf("GONE"))

        state.currentTab shouldBe MainTab.MAP
        state.returnTab shouldBe null
        state.backTarget() shouldBe null
    }

    @Test
    fun `switching tabs keeps every tab's screens`() {
        val (controller, state) = controllerWith()
        controller.goTo(DestinationSearchAction(hex = "3c6589"))

        controller.selectTab(MainTab.SEARCH)
        controller.selectTab(MainTab.MAP)

        state.currentTab shouldBe MainTab.MAP
        state.stackOf(MainTab.MAP) shouldBe listOf(DestinationMap(), DestinationSearchAction(hex = "3c6589"))
        state.stackOf(MainTab.SEARCH) shouldBe listOf(DestinationSearch())
    }

    @Test
    fun `screens open on the tab they were opened from`() {
        val (controller, state) = controllerWith()
        controller.selectTab(MainTab.WATCH)

        controller.goTo(DestinationSettingsIndex)

        state.stackOf(MainTab.WATCH) shouldBe listOf(DestinationWatchList(), DestinationSettingsIndex)
        state.stackOf(MainTab.MAP) shouldBe listOf(DestinationMap())
    }

    @Test
    fun `showing something on the map keeps the asking tab's sheet and comes back to it`() {
        val (controller, state) = controllerWith()
        controller.selectTab(MainTab.WATCH)
        controller.goTo(DestinationWatchDetails(watchId = "w1"))
        val options = MapOptions.focus("3C6589")

        controller.goTo(DestinationMap(mapOptions = options))

        state.currentTab shouldBe MainTab.MAP
        state.stackOf(MainTab.MAP) shouldBe listOf(DestinationMap())
        state.stackOf(MainTab.WATCH) shouldBe listOf(DestinationWatchList(), DestinationWatchDetails(watchId = "w1"))
        mapRequests.take() shouldBe options
        state.backTarget() shouldBe MainTab.WATCH

        controller.up() shouldBe true
        state.currentTab shouldBe MainTab.WATCH
        state.returnTab shouldBe null
    }

    @Test
    fun `a request clears the screens above the tab's own`() {
        val (controller, state) = controllerWith()
        controller.goTo(DestinationSettingsIndex)
        controller.selectTab(MainTab.SEARCH)

        controller.goTo(DestinationMap(mapOptions = MapOptions.focus("3C6589")))

        state.stackOf(MainTab.MAP) shouldBe listOf(DestinationMap())
    }

    @Test
    fun `a targeted search is a request, the plain tab is not`() {
        val (controller, _) = controllerWith()
        val target = DestinationSearch(targetHexes = listOf("3c6589"))

        controller.goTo(DestinationSearch())
        searchRequests.take() shouldBe null

        controller.goTo(target)
        searchRequests.take() shouldBe SearchTarget(hexes = listOf("3c6589"))
    }

    @Test
    fun `asking for the same thing twice applies it twice`() = runTest(UnconfinedTestDispatcher()) {
        val (controller, _) = controllerWith()
        val target = DestinationSearch(targetHexes = listOf("3c6589"))
        val applied = mutableListOf<SearchTarget>()
        val consumer = launch { searchRequests.consumeEach { applied.add(it) } }

        controller.goTo(target)
        controller.selectTab(MainTab.MAP)
        controller.goTo(target)

        applied shouldBe List(2) { SearchTarget(hexes = listOf("3c6589")) }
        consumer.cancel()
    }

    @Test
    fun `requests made before the tab takes one leave only the newest`() {
        val (controller, _) = controllerWith()

        controller.goTo(DestinationMap(mapOptions = MapOptions.focus("AAAAAA")))
        controller.goTo(DestinationMap(mapOptions = MapOptions.focus("BBBBBB")))

        mapRequests.take() shouldBe MapOptions.focus("BBBBBB")
        mapRequests.take() shouldBe null
    }

    @Test
    fun `back pops the tab's own screens first, then goes to the map, then leaves`() {
        val (controller, state) = controllerWith()
        controller.selectTab(MainTab.SEARCH)
        controller.goTo(DestinationSearchAction(hex = "3c6589"))

        controller.up() shouldBe true
        state.stackOf(MainTab.SEARCH) shouldBe listOf(DestinationSearch())
        state.currentTab shouldBe MainTab.SEARCH

        controller.up() shouldBe true
        state.currentTab shouldBe MainTab.MAP

        controller.up() shouldBe false
    }

    @Test
    fun `picking a tab forgets where a request came from`() {
        val (controller, state) = controllerWith()
        controller.selectTab(MainTab.WATCH)
        controller.goTo(DestinationSearch(targetSquawks = listOf("7700")))

        controller.selectTab(MainTab.MAP)
        controller.selectTab(MainTab.SEARCH)

        state.backTarget() shouldBe MainTab.MAP
    }

    @Test
    fun `navigation before the tabs are shown lands in the tabs and stays there`() {
        val (controller, state) = controllerWith(NavigationState(setup))

        controller.goTo(DestinationWatchList())
        controller.goTo(DestinationWatchDetails(watchId = "w1"))
        controller.enterTabs()

        state.inTabs shouldBe true
        state.currentTab shouldBe MainTab.WATCH
        state.returnTab shouldBe null
        state.stackOf(MainTab.WATCH) shouldBe listOf(DestinationWatchList(), DestinationWatchDetails(watchId = "w1"))
        state.bootstrap.toList() shouldBe listOf(DestinationMain)
    }

    @Test
    fun `onboarding stays before the tabs and finishing it starts over at main`() {
        val (controller, state) = controllerWith(NavigationState(setup))

        controller.goTo(DestinationWelcome)
        controller.goTo(DestinationPrivacy)
        state.bootstrap.toList() shouldBe listOf(DestinationMain, DestinationWelcome, DestinationPrivacy)

        controller.goTo(DestinationMain, popUpTo = DestinationMain, inclusive = true)
        state.bootstrap.toList() shouldBe listOf(DestinationMain)
        state.inTabs shouldBe false
    }
}
