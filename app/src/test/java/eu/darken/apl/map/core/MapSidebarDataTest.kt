package eu.darken.apl.map.core

import eu.darken.apl.map.core.MapSidebarData.Controls
import eu.darken.apl.map.core.MapSidebarData.SortField
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class MapSidebarDataTest : BaseTest() {

    private fun plane(
        hex: String,
        callsign: String? = null,
        type: String? = null,
        altitudeFt: Int? = null,
        onGround: Boolean = false,
        speed: Int? = null,
        lat: Double = 50.0,
        lon: Double = 8.0,
    ) = MapPlane(
        hex = hex,
        latitude = lat,
        longitude = lon,
        trackDegrees = null,
        altitudeFt = altitudeFt,
        groundSpeedKnots = speed,
        onGround = onGround,
        callsign = callsign,
        aircraftType = type,
        category = null,
        military = false,
        opacity = 1f,
    )

    private fun build(
        planes: List<MapPlane>,
        controls: Controls = Controls(),
        watched: Set<String> = emptySet(),
        center: Pair<Double, Double>? = null,
    ) = MapSidebarData.build(
        planes = planes,
        controls = controls,
        watched = watched,
        center = center,
        shapes = null,
        mapFilter = null,
        serverLimited = false,
    )

    private val MapSidebarData.hexes get() = matches.map { it.hex }

    @Test
    fun `filtering happens before the row limit`() {
        val planes = (0 until 300).map { plane(hex = "%06X".format(it), callsign = if (it == 299) "DLH42" else "X$it") }

        val data = build(planes, Controls(query = "dlh"))

        data.inView shouldBe 300
        data.hexes shouldContainExactly listOf("00012B")
    }

    @Test
    fun `the filter matches callsign, type and hex, ignoring case and outer blanks`() {
        val planes = listOf(
            plane(hex = "3C6444", callsign = "DLH4AB", type = "A320"),
            plane(hex = "A1B2C3", callsign = "UAL1", type = "B738"),
            plane(hex = "4CA123", callsign = "RYR7"),
        )

        build(planes, Controls(query = " a32 ")).hexes shouldContainExactly listOf("3C6444")
        build(planes, Controls(query = "b2c")).hexes shouldContainExactly listOf("A1B2C3")
        build(planes, Controls(query = "ryr")).hexes shouldContainExactly listOf("4CA123")
    }

    @Test
    fun `watched only keeps the watched aircraft and marks them`() {
        val planes = listOf(plane(hex = "AAAAAA"), plane(hex = "BBBBBB"))

        val data = build(planes, Controls(watchedOnly = true), watched = setOf("BBBBBB"))

        data.hexes shouldContainExactly listOf("BBBBBB")
        data.matches.single().watched shouldBe true
        build(planes, watched = setOf("BBBBBB")).matches.map { it.watched } shouldContainExactly listOf(false, true)
    }

    @Test
    fun `ground sorts below any altitude and unknown altitudes come last either way`() {
        val planes = listOf(
            plane(hex = "000001", altitudeFt = 3_000),
            plane(hex = "000002", altitudeFt = null),
            plane(hex = "000003", altitudeFt = 300, onGround = true),
            plane(hex = "000004", altitudeFt = -200),
        )

        build(planes, Controls(sort = SortField.ALTITUDE, ascending = true)).hexes shouldContainExactly
                listOf("000003", "000004", "000001", "000002")
        build(planes, Controls(sort = SortField.ALTITUDE, ascending = false)).hexes shouldContainExactly
                listOf("000001", "000004", "000003", "000002")
    }

    @Test
    fun `equal values keep a fixed order by hex`() {
        val planes = listOf(
            plane(hex = "CCCCCC", speed = 400),
            plane(hex = "AAAAAA", speed = 400),
            plane(hex = "BBBBBB", speed = 400),
        )

        build(planes, Controls(sort = SortField.SPEED, ascending = false)).hexes shouldContainExactly
                listOf("AAAAAA", "BBBBBB", "CCCCCC")
    }

    @Test
    fun `aircraft without a callsign sort by their hex among the callsigns`() {
        val planes = listOf(
            plane(hex = "BBBBBB", callsign = "CFG1"),
            plane(hex = "AAAAAA"),
            plane(hex = "CCCCCC", callsign = "  "),
        )

        build(planes).hexes shouldContainExactly listOf("AAAAAA", "CCCCCC", "BBBBBB")
        build(planes).matches[1].callsign shouldBe null
    }

    @Test
    fun `distance is measured from the center only while sorted by it`() {
        val planes = listOf(
            plane(hex = "FAR000", lat = 51.0, lon = 8.0),
            plane(hex = "NEAR00", lat = 50.1, lon = 8.0),
        )
        val center = 50.0 to 8.0

        val byDistance = build(planes, Controls(sort = SortField.DISTANCE), center = center)
        byDistance.hexes shouldContainExactly listOf("NEAR00", "FAR000")
        byDistance.matches.first().distanceMeters!! shouldBe (11_120.0 plusOrMinus 50.0)

        build(planes, center = center).matches.map { it.distanceMeters } shouldContainExactly listOf(null, null)
    }

    @Test
    fun `without a center the distance sort keeps the hex order`() {
        val planes = listOf(plane(hex = "BBBBBB"), plane(hex = "AAAAAA"))

        build(planes, Controls(sort = SortField.DISTANCE)).hexes shouldContainExactly listOf("AAAAAA", "BBBBBB")
    }
}
