package eu.darken.apl.map.core

import eu.darken.apl.server.api.AircraftPosition
import eu.darken.apl.server.api.MapAircraft
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class RecentTracksTest : BaseTest() {

    private fun at(id: String, observedAt: Long?) = id.uppercase() to MapAircraft(
        id = id,
        position = AircraftPosition(50.0, 8.0 + (observedAt ?: 0) / 1_000_000.0, observedAt),
    )

    private fun RecentTracks.times(hex: String) = points(hex)?.map { it.observedAt }

    @Test
    fun `positions accumulate once each and aircraft that left are dropped`() {
        val tracks = RecentTracks(pointsPerAircraft = 3, maxAircraft = 10)

        tracks.record(mapOf(at("aaaaaa", 1_000), at("bbbbbb", 1_000)))
        tracks.record(mapOf(at("aaaaaa", 1_000), at("bbbbbb", 2_000)))
        tracks.record(mapOf(at("aaaaaa", 3_000)))

        tracks.times("AAAAAA") shouldBe listOf(1_000L, 3_000L)
        tracks.times("BBBBBB") shouldBe null
    }

    @Test
    fun `each track and the number of tracks are bounded`() {
        val tracks = RecentTracks(pointsPerAircraft = 2, maxAircraft = 1)

        tracks.record(mapOf(at("aaaaaa", 1_000), at("bbbbbb", 1_000)))
        tracks.record(mapOf(at("aaaaaa", 2_000), at("bbbbbb", 2_000)))
        tracks.record(mapOf(at("aaaaaa", 3_000), at("bbbbbb", 3_000)))

        tracks.times("AAAAAA") shouldBe listOf(2_000L, 3_000L)
        tracks.times("BBBBBB") shouldBe null
    }

    @Test
    fun `aircraft on screen take the places of others once all are taken`() {
        val tracks = RecentTracks(maxAircraft = 2)
        tracks.record(mapOf(at("aaaaaa", 1_000), at("bbbbbb", 1_000)))

        // Later in the answer than the two tracked ones, but drawn
        tracks.record(mapOf(at("aaaaaa", 2_000), at("bbbbbb", 2_000), at("cccccc", 2_000)), preferred = setOf("CCCCCC"))

        tracks.times("AAAAAA") shouldBe null
        tracks.times("BBBBBB") shouldBe listOf(1_000L, 2_000L)
        tracks.times("CCCCCC") shouldBe listOf(2_000L)
    }

    @Test
    fun `aircraft on screen keep their places against other aircraft on screen`() {
        val tracks = RecentTracks(maxAircraft = 1)
        tracks.record(mapOf(at("aaaaaa", 1_000)), preferred = setOf("AAAAAA"))

        tracks.record(mapOf(at("aaaaaa", 2_000), at("bbbbbb", 2_000)), preferred = setOf("AAAAAA", "BBBBBB"))

        tracks.times("AAAAAA") shouldBe listOf(1_000L, 2_000L)
        tracks.times("BBBBBB") shouldBe null
    }

    @Test
    fun `positions without a time are skipped`() {
        val tracks = RecentTracks()
        tracks.record(mapOf(at("aaaaaa", null)))

        tracks.points("AAAAAA") shouldBe null
    }
}
