package eu.darken.apl.main.ui.navigation

import eu.darken.apl.feeder.ui.DestinationFeederList
import eu.darken.apl.main.ui.settings.DestinationSettingsIndex
import eu.darken.apl.map.core.MapOptions
import eu.darken.apl.map.ui.DestinationMap
import eu.darken.apl.map.ui.MapRequests
import eu.darken.apl.search.ui.DestinationSearch
import eu.darken.apl.search.ui.SearchRequests
import eu.darken.apl.search.ui.SearchTarget
import eu.darken.apl.search.ui.actions.DestinationSearchAction
import eu.darken.apl.watch.ui.DestinationWatchList
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class MainTabSetupTest : BaseTest() {

    private val mapRequests = MapRequests()
    private val searchRequests = SearchRequests()
    private val setup = MainTabSetup(mapRequests, searchRequests)

    @Test
    fun `each tab's own screen leads to it, other screens to none`() {
        setup.tabOf(DestinationMap()) shouldBe MainTab.MAP
        setup.tabOf(DestinationMap(mapOptions = MapOptions.focus("3C6589"))) shouldBe MainTab.MAP
        setup.tabOf(DestinationSearch()) shouldBe MainTab.SEARCH
        setup.tabOf(DestinationSearch(targetSquawks = listOf("7700"))) shouldBe MainTab.SEARCH
        setup.tabOf(DestinationWatchList()) shouldBe MainTab.WATCH
        setup.tabOf(DestinationFeederList) shouldBe MainTab.FEEDER
        setup.tabOf(DestinationSettingsIndex) shouldBe null
        setup.tabOf(DestinationSearchAction(hex = "3c6589")) shouldBe null
    }

    @Test
    fun `each tab starts at its own screen`() {
        MainTab.entries.forEach { setup.tabOf(it.root) shouldBe it }
    }

    @Test
    fun `a map asked for with options hands them to the map`() {
        val options = MapOptions.focus("3C6589")

        setup.deliver(DestinationMap(mapOptions = options))

        mapRequests.take() shouldBe options
    }

    @Test
    fun `the plain tabs ask for nothing`() {
        setup.deliver(DestinationMap())
        setup.deliver(DestinationSearch())
        setup.deliver(DestinationWatchList())
        setup.deliver(DestinationFeederList)

        mapRequests.take() shouldBe null
        searchRequests.take() shouldBe null
    }

    @Test
    fun `a search asked for keeps which list each target came in`() {
        listOf(
            DestinationSearch(targetHexes = listOf("3c6589")) to SearchTarget(hexes = listOf("3c6589")),
            DestinationSearch(targetSquawks = listOf("7700")) to SearchTarget(squawks = listOf("7700")),
            DestinationSearch(targetCallsigns = listOf("DLH453")) to SearchTarget(callsigns = listOf("DLH453")),
            DestinationSearch(targetHexes = emptyList()) to SearchTarget(hexes = emptyList()),
            DestinationSearch(targetHexes = listOf("3c6589"), targetSquawks = listOf("7700")) to
                SearchTarget(hexes = listOf("3c6589"), squawks = listOf("7700")),
        ).forEach { (destination, target) ->
            setup.deliver(destination)
            searchRequests.take() shouldBe target
        }
    }

    @Test
    fun `opening the plain tab leaves a waiting search alone`() {
        setup.deliver(DestinationSearch(targetSquawks = listOf("7700")))

        setup.deliver(DestinationSearch())

        searchRequests.take() shouldBe SearchTarget(squawks = listOf("7700"))
    }
}
