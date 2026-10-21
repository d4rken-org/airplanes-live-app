package eu.darken.apl.map.core

import eu.darken.apl.watch.core.db.types.AircraftWatchEntity
import eu.darken.apl.watch.core.db.types.BaseWatchEntity
import eu.darken.apl.watch.core.db.types.FlightWatchEntity
import eu.darken.apl.watch.core.db.types.LocationWatchEntity
import eu.darken.apl.watch.core.db.types.SquawkWatchEntity
import eu.darken.apl.watch.core.types.AircraftWatch
import eu.darken.apl.watch.core.types.FlightWatch
import eu.darken.apl.watch.core.types.LocationWatch
import eu.darken.apl.watch.core.types.SquawkWatch
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class MapWatchesTest : BaseTest() {

    private val watches = MapWatches.from(
        listOf(
            AircraftWatch(
                BaseWatchEntity(id = "hex", watchType = AircraftWatchEntity.TYPE_KEY),
                AircraftWatchEntity(id = "hex", hexCode = " 3c65a3"),
            ),
            FlightWatch(
                BaseWatchEntity(id = "flight", watchType = FlightWatchEntity.TYPE_KEY),
                FlightWatchEntity(id = "flight", callsign = "dlh453 "),
            ),
            SquawkWatch(
                BaseWatchEntity(id = "squawk", watchType = SquawkWatchEntity.TYPE_KEY),
                SquawkWatchEntity(id = "squawk", code = "0425"),
            ),
            SquawkWatch(
                BaseWatchEntity(id = "blank", watchType = SquawkWatchEntity.TYPE_KEY),
                SquawkWatchEntity(id = "blank", code = " "),
            ),
            LocationWatch(
                BaseWatchEntity(id = "zone", watchType = LocationWatchEntity.TYPE_KEY, latitude = 50.0, longitude = 8.0, radius = 50_000f),
                LocationWatchEntity(id = "zone", label = "FRA"),
            ),
        )
    )

    private fun plane(hex: String = "AAAAAA", callsign: String? = null, squawk: String? = null) = MapPlane(
        hex = hex,
        latitude = 50.0,
        longitude = 8.0,
        trackDegrees = null,
        altitudeFt = 36_000,
        groundSpeedKnots = null,
        onGround = false,
        callsign = callsign,
        squawk = squawk,
        aircraftType = null,
        category = null,
        military = false,
        opacity = 1f,
    )

    @Test
    fun `hex, callsign and squawk match regardless of case and padding`() {
        watches.hexes shouldBe setOf("3C65A3")
        watches.matches(plane(hex = "3C65A3")) shouldBe true
        watches.matches(plane(hex = "3c65a3 ")) shouldBe true
        watches.matches(plane(callsign = "DLH453")) shouldBe true
        watches.matches(plane(squawk = "0425")) shouldBe true
    }

    @Test
    fun `a squawk keeps its leading zero`() {
        watches.matches(plane(squawk = "425")) shouldBe false
    }

    @Test
    fun `missing or blank values match nothing, also not a blank watch`() {
        watches.squawks.keys shouldBe setOf("0425")
        watches.matches(plane(callsign = null, squawk = null)) shouldBe false
        watches.matches(plane(squawk = " ")) shouldBe false
    }

    @Test
    fun `an aircraft inside a location watch is not a match`() {
        watches.matches(plane(hex = "BBBBBB")) shouldBe false
    }

    @Test
    fun `matching lists the callsign and squawk watches, the hex watch has its own button`() {
        watches.matching(callsign = "DLH453", squawk = "0425") shouldBe listOf(
            MapWatches.Match("flight", MapWatches.Match.Kind.CALLSIGN, "DLH453"),
            MapWatches.Match("squawk", MapWatches.Match.Kind.SQUAWK, "0425"),
        )
        watches.matching(callsign = null, squawk = null) shouldBe emptyList()
    }
}
