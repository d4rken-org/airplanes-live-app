package eu.darken.apl.map.core

import io.kotest.matchers.collections.shouldContainExactly
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class MapSidebarDataTest : BaseTest() {

    private fun aircraft(hex: String, altitudeFt: Int?, onGround: Boolean = false, speedKt: Int? = null) =
        MapSidebarData.SidebarAircraft(
            hex = hex,
            callsign = null,
            icaoType = null,
            country = null,
            onGround = onGround,
            altitudeFt = altitudeFt,
            speedKt = speedKt,
        )

    private fun data(vararg aircraft: MapSidebarData.SidebarAircraft) =
        MapSidebarData(totalAircraft = aircraft.size, onScreen = aircraft.size, aircraft = aircraft.toList())

    @Test
    fun `altitude sorts numerically, ground as lowest and unknown first`() {
        val sorted = data(
            aircraft("high", 35_000),
            aircraft("unknown", null),
            aircraft("ground", 50, onGround = true),
            aircraft("low", 9_000),
        ).sortedBy(MapSidebarData.SortField.ALTITUDE, ascending = true)

        sorted.aircraft.map { it.hex } shouldContainExactly listOf("unknown", "ground", "low", "high")
    }

    @Test
    fun `speed sorts numerically, not by text`() {
        val sorted = data(
            aircraft("a", null, speedKt = 90),
            aircraft("b", null, speedKt = 450),
            aircraft("c", null, speedKt = 1_000),
        ).sortedBy(MapSidebarData.SortField.SPEED, ascending = false)

        sorted.aircraft.map { it.hex } shouldContainExactly listOf("c", "b", "a")
    }
}
