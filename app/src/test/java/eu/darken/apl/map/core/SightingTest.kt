package eu.darken.apl.map.core

import android.location.Location
import eu.darken.apl.common.compose.preview.FakeAircraft
import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.server.api.AircraftPosition
import eu.darken.apl.server.api.MapAircraft
import eu.darken.apl.server.api.TrailPoint
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.time.Instant

class SightingTest : BaseTest() {

    private fun location(lat: Double, lon: Double) = mockk<Location> {
        every { latitude } returns lat
        every { longitude } returns lon
    }

    private fun details(hex: String = "3C65A3", lat: Double, observedAt: Long, track: Float? = 90f) = FakeAircraft(
        hex = hex,
        location = location(lat, 10.0),
        positionSeenAt = Instant.ofEpochMilli(observedAt),
        groundTrack = track,
    )

    private fun aircraft(lat: Double, observedAt: Long?, track: Double? = 180.0) =
        MapAircraft(id = "3c65a3", position = AircraftPosition(lat, 10.0, observedAt), trackDegrees = track)

    private fun frame(
        selectedAircraft: MapAircraft? = null,
        selectedDetails: Aircraft? = null,
        trailNewest: TrailPoint? = null,
        replay: MapAircraftProvider.Replay? = null,
        selectedHex: String? = "3C65A3",
    ) = MapAircraftProvider.Frame(
        traffic = emptyList(), trafficVersion = 1, fades = emptyMap(), selectedHex = selectedHex, selected = null,
        selectedDetails = selectedDetails, selectedMissing = null, selectedTrail = emptyList(), selectedTrailHead = null,
        tracks = null, viewport = null, onScreen = emptyList(), shownCount = 0, capped = false, totalMatching = null,
        replay = replay, selectedAircraft = selectedAircraft, selectedTrailNewest = trailNewest,
    )

    @Test
    fun `an aircraft is sighted only with both a position and its time`() {
        details(lat = 51.0, observedAt = 1_000).sighting() shouldBe Sighting(51.0, 10.0, 1_000, 90f)
        FakeAircraft(location = location(51.0, 10.0), positionSeenAt = null).sighting() shouldBe null
        FakeAircraft(location = null, positionSeenAt = Instant.ofEpochMilli(1_000)).sighting() shouldBe null
    }

    @Test
    fun `the newest of the drawn aircraft, the answer and the trail wins`() {
        val sources = frame(
            selectedAircraft = aircraft(51.0, observedAt = 3_000),
            selectedDetails = details(lat = 52.0, observedAt = 2_000),
            trailNewest = TrailPoint(53.0, 10.0, null, 1_000),
        )
        sources.selectedSighting() shouldBe Sighting(51.0, 10.0, 3_000, 180f)

        frame(
            selectedAircraft = aircraft(51.0, observedAt = 1_000),
            selectedDetails = details(lat = 52.0, observedAt = 2_000),
        ).selectedSighting() shouldBe Sighting(52.0, 10.0, 2_000, 90f)
    }

    @Test
    fun `a newest trail point takes the aircraft's track, or the answer's without one`() {
        frame(
            selectedAircraft = aircraft(51.0, observedAt = 1_000, track = 180.0),
            selectedDetails = details(lat = 52.0, observedAt = 500, track = 90f),
            trailNewest = TrailPoint(53.0, 10.0, null, 2_000),
        ).selectedSighting() shouldBe Sighting(53.0, 10.0, 2_000, 180f)

        frame(
            selectedDetails = details(lat = 52.0, observedAt = 500, track = 90f),
            trailNewest = TrailPoint(53.0, 10.0, null, 2_000),
        ).selectedSighting() shouldBe Sighting(53.0, 10.0, 2_000, 90f)
    }

    @Test
    fun `nothing is sighted in the history, without a selection, or from another aircraft's answer`() {
        frame(
            selectedAircraft = aircraft(51.0, observedAt = 1_000),
            replay = mockk(),
        ).selectedSighting() shouldBe null

        frame(selectedAircraft = aircraft(51.0, observedAt = 1_000), selectedHex = null).selectedSighting() shouldBe null

        frame(selectedDetails = details(hex = "4B1805", lat = 52.0, observedAt = 2_000)).selectedSighting() shouldBe null
    }
}
