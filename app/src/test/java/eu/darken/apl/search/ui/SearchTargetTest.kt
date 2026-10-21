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
}
