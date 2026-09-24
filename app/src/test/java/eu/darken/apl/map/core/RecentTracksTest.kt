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

    @Test
    fun `positions accumulate once each and aircraft that left are dropped`() {
        val tracks = RecentTracks(pointsPerAircraft = 3, maxAircraft = 10)

        tracks.record(mapOf(at("aaaaaa", 1_000), at("bbbbbb", 1_000)))
        tracks.record(mapOf(at("aaaaaa", 1_000), at("bbbbbb", 2_000)))
        tracks.record(mapOf(at("aaaaaa", 3_000)))

        tracks.snapshot().mapValues { (_, points) -> points.map { it.observedAt } } shouldBe mapOf(
            "AAAAAA" to listOf(1_000L, 3_000L),
        )
    }

    @Test
    fun `each track and the number of tracks are bounded`() {
        val tracks = RecentTracks(pointsPerAircraft = 2, maxAircraft = 1)

        tracks.record(mapOf(at("aaaaaa", 1_000), at("bbbbbb", 1_000)))
        tracks.record(mapOf(at("aaaaaa", 2_000), at("bbbbbb", 2_000)))
        tracks.record(mapOf(at("aaaaaa", 3_000), at("bbbbbb", 3_000)))

        tracks.snapshot().keys shouldBe setOf("AAAAAA")
        tracks.snapshot().getValue("AAAAAA").map { it.observedAt } shouldBe listOf(2_000L, 3_000L)
    }

    @Test
    fun `positions without a time are skipped`() {
        val tracks = RecentTracks()
        tracks.record(mapOf(at("aaaaaa", null)))

        tracks.snapshot() shouldBe emptyMap()
    }
}
