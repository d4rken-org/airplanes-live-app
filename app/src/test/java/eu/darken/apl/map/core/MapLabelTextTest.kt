package eu.darken.apl.map.core

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class MapLabelTextTest : BaseTest() {

    private fun plane(
        callsign: String? = "DLH453",
        aircraftType: String? = "A320",
        altitudeFt: Int? = 36_000,
        groundSpeedKnots: Int? = 450,
        onGround: Boolean = false,
    ) = MapPlane(
        hex = "3C65A3",
        latitude = 50.0,
        longitude = 8.0,
        trackDegrees = 90f,
        altitudeFt = altitudeFt,
        groundSpeedKnots = groundSpeedKnots,
        onGround = onGround,
        callsign = callsign,
        squawk = null,
        aircraftType = aircraftType,
        category = null,
        military = false,
        opacity = 1f,
    )

    private fun text(fields: MapLabelFields) = MapLabelText(
        fields = fields,
        altitudeText = { "$it ft" },
        groundText = "GND",
        speedText = { "$it kt" },
    )

    private val all = MapLabelFields(callsign = true, type = true, altitude = true, speed = true)

    @Test
    fun `defaults show the callsign`() {
        text(MapLabelFields()).label(plane(), selected = false) shouldBe "DLH453"
    }

    @Test
    fun `without a callsign the hex shows`() {
        text(MapLabelFields()).label(plane(callsign = null), selected = false) shouldBe "3C65A3"
    }

    @Test
    fun `all fields stack top to bottom`() {
        text(all).label(plane(), selected = false) shouldBe "DLH453\nA320\n36000 ft  450 kt"
    }

    @Test
    fun `disabled fields drop out`() {
        text(all.copy(callsign = false)).label(plane(), selected = false) shouldBe "A320\n36000 ft  450 kt"
        text(all.copy(type = false)).label(plane(), selected = false) shouldBe "DLH453\n36000 ft  450 kt"
        text(all.copy(altitude = false)).label(plane(), selected = false) shouldBe "DLH453\nA320\n450 kt"
        text(all.copy(speed = false)).label(plane(), selected = false) shouldBe "DLH453\nA320\n36000 ft"
    }

    @Test
    fun `unknown values drop out`() {
        text(all).label(plane(aircraftType = null), selected = false) shouldBe "DLH453\n36000 ft  450 kt"
        text(all).label(plane(aircraftType = " "), selected = false) shouldBe "DLH453\n36000 ft  450 kt"
        text(all).label(plane(altitudeFt = null), selected = false) shouldBe "DLH453\nA320\n450 kt"
        text(all).label(plane(groundSpeedKnots = null), selected = false) shouldBe "DLH453\nA320\n36000 ft"
        text(all).label(plane(altitudeFt = null, groundSpeedKnots = null), selected = false) shouldBe "DLH453\nA320"
    }

    @Test
    fun `altitude only`() {
        text(MapLabelFields(callsign = false, altitude = true)).label(plane(), selected = false) shouldBe "36000 ft"
    }

    @Test
    fun `speed only`() {
        text(MapLabelFields(callsign = false, speed = true)).label(plane(), selected = false) shouldBe "450 kt"
    }

    @Test
    fun `nothing applicable gives an empty label`() {
        val fields = MapLabelFields(callsign = false, type = true, altitude = true)
        text(fields).label(plane(aircraftType = null, altitudeFt = null), selected = false) shouldBe ""
    }

    @Test
    fun `slow on the ground hides altitude and speed unless selected`() {
        val slow = plane(groundSpeedKnots = 18, onGround = true)
        text(all).label(slow, selected = false) shouldBe "DLH453\nA320"
        text(all).label(slow, selected = true) shouldBe "DLH453\nA320\nGND  18 kt"
    }

    @Test
    fun `unknown speed on the ground hides altitude unless selected`() {
        val parked = plane(groundSpeedKnots = null, onGround = true)
        text(all).label(parked, selected = false) shouldBe "DLH453\nA320"
        text(all).label(parked, selected = true) shouldBe "DLH453\nA320\nGND"
    }

    @Test
    fun `moving on the ground shows the ground text and speed`() {
        text(all).label(plane(groundSpeedKnots = 19, onGround = true), selected = false) shouldBe "DLH453\nA320\nGND  19 kt"
    }

    @Test
    fun `formatting goes through the supplied functions`() {
        val custom = MapLabelText(
            fields = MapLabelFields(callsign = false, altitude = true, speed = true),
            altitudeText = { "alt=$it" },
            groundText = "on ground",
            speedText = { "spd=$it" },
        )
        custom.label(plane(altitudeFt = 1200, groundSpeedKnots = 140), selected = false) shouldBe "alt=1200  spd=140"
        custom.label(plane(groundSpeedKnots = 25, onGround = true), selected = false) shouldBe "on ground  spd=25"
    }
}
