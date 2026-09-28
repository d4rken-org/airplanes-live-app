package eu.darken.apl.common.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import eu.darken.apl.map.ui.DestinationMap
import eu.darken.apl.search.ui.DestinationSearch
import eu.darken.apl.search.ui.actions.DestinationSearchAction
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class NavigationControllerTest : BaseTest() {

    private fun controllerWith(vararg entries: NavKey): Pair<NavigationController, NavBackStack<NavKey>> {
        val backStack = NavBackStack(*entries)
        return NavigationController().apply { setup(backStack) } to backStack
    }

    @Test
    fun `opening search again replaces the earlier search`() {
        val (controller, backStack) = controllerWith(DestinationSearch(), DestinationMap())

        controller.goTo(DestinationSearch(targetHexes = listOf("3c6589"), requestId = "a"))

        backStack.toList() shouldBe listOf(DestinationMap(), DestinationSearch(targetHexes = listOf("3c6589"), requestId = "a"))
    }

    @Test
    fun `a sheet over the replaced search goes with it`() {
        val (controller, backStack) = controllerWith(
            DestinationSearch(),
            DestinationSearchAction(hex = "3c6589"),
            DestinationMap(),
        )

        controller.goTo(DestinationSearch(targetHexes = listOf("3c6589"), requestId = "a"))

        backStack.toList() shouldBe listOf(DestinationMap(), DestinationSearch(targetHexes = listOf("3c6589"), requestId = "a"))
    }

    @Test
    fun `switching to the search tab leaves one search`() {
        val (controller, backStack) = controllerWith(DestinationSearch(targetHexes = listOf("3c6589"), requestId = "a"), DestinationMap())

        controller.replace(DestinationSearch())

        backStack.toList() shouldBe listOf(DestinationSearch())
    }

    @Test
    fun `other destinations may repeat`() {
        val (controller, backStack) = controllerWith(DestinationMap(), DestinationSearch())

        controller.goTo(DestinationMap())

        backStack.toList() shouldBe listOf(DestinationMap(), DestinationSearch(), DestinationMap())
    }
}
