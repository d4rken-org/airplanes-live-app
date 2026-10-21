package eu.darken.apl.map.ui

import eu.darken.apl.map.core.MapSidebarData
import eu.darken.apl.map.core.MapSidebarData.SidebarAircraft
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class MapListRowsTest : BaseTest() {

    private fun aircraft(hex: String, altitudeFt: Int? = null) = SidebarAircraft(
        hex = hex,
        callsign = null,
        icaoType = null,
        countryCode = null,
        altitudeFt = altitudeFt,
        onGround = false,
        speedKnots = null,
        military = false,
        shape = null,
        watched = false,
        distanceMeters = null,
    )

    private fun List<ListRow>.described() = map { if (it.gone) "${it.aircraft.hex}-gone" else it.aircraft.hex }

    @Test
    fun `released rows follow the matches up to the row limit`() {
        val matches = (0 until MapSidebarData.ROW_LIMIT + 5).map { aircraft("%06X".format(it)) }

        val rows = listRows(emptyList(), matches, held = false)

        rows.size shouldBe MapSidebarData.ROW_LIMIT
        rows.first().aircraft.hex shouldBe "000000"
    }

    @Test
    fun `held rows keep their places and take new values`() {
        val shown = listRows(emptyList(), listOf(aircraft("A"), aircraft("B"), aircraft("C")), held = false)

        val rows = listRows(shown, listOf(aircraft("C"), aircraft("D"), aircraft("A", altitudeFt = 9_000)), held = true)

        rows.described() shouldContainExactly listOf("A", "B-gone", "C")
        rows.first().aircraft.altitudeFt shouldBe 9_000
    }

    @Test
    fun `a held row past the row limit is still there, not gone`() {
        val shown = listOf(ListRow(aircraft("LAST")))
        val matches = (0 until MapSidebarData.ROW_LIMIT).map { aircraft("%06X".format(it)) } + aircraft("LAST")

        listRows(shown, matches, held = true).described() shouldContainExactly listOf("LAST")
    }

    @Test
    fun `gone rows leave once the hold ends`() {
        val shown = listOf(ListRow(aircraft("A")), ListRow(aircraft("B"), gone = true))

        listRows(shown, listOf(aircraft("A")), held = false).described() shouldContainExactly listOf("A")
    }

    @Test
    fun `held changes are new aircraft, a new order or gone rows`() {
        val shown = listOf(ListRow(aircraft("A")), ListRow(aircraft("B")))

        hasHeldChanges(shown, listOf(aircraft("A"), aircraft("B"))) shouldBe false
        hasHeldChanges(shown, listOf(aircraft("B"), aircraft("A"))) shouldBe true
        hasHeldChanges(shown, listOf(aircraft("A"), aircraft("B"), aircraft("C"))) shouldBe true
        hasHeldChanges(listOf(ListRow(aircraft("A")), ListRow(aircraft("B"), gone = true)), listOf(aircraft("A"))) shouldBe true
    }
}
