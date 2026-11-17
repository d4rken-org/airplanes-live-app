package eu.darken.apl.search.ui

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class SearchTargetTest : BaseTest() {

    @Test
    fun `hexes come before squawks, squawks before callsigns`() {
        SearchTarget(hexes = listOf("3c6589"), callsigns = listOf("DLH453")).terms shouldBe listOf("3c6589")
        SearchTarget(squawks = listOf("7700"), callsigns = listOf("DLH453")).terms shouldBe listOf("7700")
        SearchTarget(callsigns = listOf("DLH453")).terms shouldBe listOf("DLH453")
    }

    @Test
    fun `an empty list still counts, only a missing one falls through`() {
        SearchTarget(hexes = emptyList(), callsigns = listOf("DLH453")).terms shouldBe emptyList()
        SearchTarget().terms shouldBe null
    }

    @Test
    fun `text is searched only when no list is given`() {
        SearchTarget(text = "B738").terms shouldBe listOf("B738")
        SearchTarget(callsigns = listOf("DLH453"), text = "B738").terms shouldBe listOf("DLH453")
        SearchTarget(hexes = emptyList(), text = "B738").terms shouldBe emptyList()
    }

    @Test
    fun `blank text searches nothing`() {
        SearchTarget(text = "").terms shouldBe null
        SearchTarget(text = "  ").terms shouldBe null
    }
}
