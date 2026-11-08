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

    private fun aircraft(
        lat: Double,
        observedAt: Long?,
        track: Double? = 180.0,
        onGround: Boolean? = null,
        heading: Double? = null,
    ) = MapAircraft(
        id = "3c65a3",
        position = AircraftPosition(lat, 10.0, observedAt),
        onGround = onGround,
        trackDegrees = track,
        trueHeadingDegrees = heading,
    )

    private fun frame(
        selectedAircraft: MapAircraft? = null,
        selectedDetails: Aircraft? = null,
        trailNewest: TrailPoint? = null,
        replay: MapAircraftProvider.Replay? = null,
        selectedHex: String? = "3C65A3",
        lastSeen: Sighting? = null,
        rememberedBearing: Float? = null,
    ) = MapAircraftProvider.Frame(
        traffic = emptyList(), trafficVersion = 1, fades = emptyMap(), selectedHex = selectedHex, selected = null,
        selectedDetails = selectedDetails, selectedMissing = null, selectedTrail = emptyList(), selectedTrailHead = null,
        tracks = null, viewport = null, onScreen = emptyList(), shownCount = 0, capped = false, totalMatching = null,
        replay = replay, selectedAircraft = selectedAircraft, selectedTrailNewest = trailNewest,
        selectedLastSeen = lastSeen,
        selectedRememberedBearing = rememberedBearing,
    )

    @Test
    fun `an aircraft on the ground is sighted pointing where its nose is`() {
        frame(
            selectedAircraft = aircraft(51.0, observedAt = 1_000, track = null, onGround = true, heading = 270.0),
        ).selectedSighting() shouldBe Sighting(51.0, 10.0, 1_000, 270f)

        frame(
            selectedAircraft = aircraft(51.0, observedAt = 1_000, track = null, onGround = true),
            rememberedBearing = 120f,
        ).selectedSighting() shouldBe Sighting(51.0, 10.0, 1_000, 120f)

        // Left out of the answer, its remembered direction still points the last seen marker
        frame(
            selectedDetails = details(lat = 52.0, observedAt = 500, track = null).copy(trackheading = null),
            lastSeen = Sighting(54.0, 10.0, 3_000),
            rememberedBearing = 120f,
        ).selectedSighting() shouldBe Sighting(54.0, 10.0, 3_000, 120f)

        FakeAircraft(
            location = location(52.0, 10.0),
            positionSeenAt = Instant.ofEpochMilli(2_000),
            onGround = true,
            groundTrack = null,
            trackheading = 300.0,
        ).sighting() shouldBe Sighting(52.0, 10.0, 2_000, 300f)
    }

    @Test
    fun `a cached position points along its own track before a heading that may be newer`() {
        FakeAircraft(
            location = location(52.0, 10.0),
            positionSeenAt = Instant.ofEpochMilli(2_000),
            onGround = true,
            groundTrack = 250f,
            trackheading = 70.0,
        ).sighting() shouldBe Sighting(52.0, 10.0, 2_000, 250f)
    }

    @Test
    fun `the answer's record on the ground points where its nose is, also when pushed back`() {
        frame(
            selectedDetails = details(lat = 52.0, observedAt = 2_000, track = 90f).copy(onGround = true, trackheading = 270.0),
        ).selectedSighting() shouldBe Sighting(52.0, 10.0, 2_000, 270f)
    }

    @Test
    fun `a newer cached position without a direction takes the map's`() {
        val map = Sighting(51.0, 10.0, 1_000, 120f)

        newestSighting(map, Sighting(52.0, 10.0, 2_000)) shouldBe Sighting(52.0, 10.0, 2_000, 120f)
        newestSighting(map, Sighting(52.0, 10.0, 2_000, 45f)) shouldBe Sighting(52.0, 10.0, 2_000, 45f)
        newestSighting(map, Sighting(52.0, 10.0, 500)) shouldBe map
        newestSighting(null, Sighting(52.0, 10.0, 2_000)) shouldBe Sighting(52.0, 10.0, 2_000)
        newestSighting(null, null) shouldBe null
    }

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
    fun `the server's last seen position wins only when it is the newest, keeping its own track`() {
        fun withLastSeen(lastSeen: Sighting) = frame(
            selectedAircraft = aircraft(51.0, observedAt = 1_000, track = 180.0),
            selectedDetails = details(lat = 52.0, observedAt = 2_000, track = 90f),
            trailNewest = TrailPoint(53.0, 10.0, null, 1_500),
            lastSeen = lastSeen,
        ).selectedSighting()

        withLastSeen(Sighting(54.0, 10.0, 3_000, 45f)) shouldBe Sighting(54.0, 10.0, 3_000, 45f)
        withLastSeen(Sighting(54.0, 10.0, 500, 45f)) shouldBe Sighting(52.0, 10.0, 2_000, 90f)
        // Without a track of its own it borrows the aircraft's, like a trail point
        withLastSeen(Sighting(54.0, 10.0, 3_000)) shouldBe Sighting(54.0, 10.0, 3_000, 180f)
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
