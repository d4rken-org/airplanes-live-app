package eu.darken.apl.common.flight

import android.location.Location
import eu.darken.apl.common.compose.preview.FakeAircraft
import eu.darken.apl.main.core.aircraft.Aircraft
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.time.Instant

class RouteLookupKeysTest : BaseTest() {

    private fun aircraft(callsign: String?, lat: Double, seenAt: Instant) = FakeAircraft(
        hex = "A1B2C3",
        callsign = callsign,
        location = mockk<Location> {
            every { latitude } returns lat
            every { longitude } returns 0.0
        },
        positionSeenAt = seenAt,
    )

    @Test
    fun `position updates with the same callsign give one key`() = runTest {
        val keys = flowOf<Aircraft?>(
            aircraft("BAW256", lat = 51.0, seenAt = Instant.parse("2026-11-06T12:00:00Z")),
            aircraft(" baw256 ", lat = 51.1, seenAt = Instant.parse("2026-11-06T12:00:05Z")),
        ).routeLookupKeys().toList()

        keys shouldBe listOf("A1B2C3" to "BAW256")
    }

    @Test
    fun `a new callsign gives a second key`() = runTest {
        val keys = flowOf<Aircraft?>(
            aircraft("BAW256", lat = 51.0, seenAt = Instant.parse("2026-11-06T12:00:00Z")),
            aircraft("BAW257", lat = 51.1, seenAt = Instant.parse("2026-11-06T12:00:05Z")),
        ).routeLookupKeys().toList()

        keys shouldBe listOf("A1B2C3" to "BAW256", "A1B2C3" to "BAW257")
    }

    @Test
    fun `missing aircraft are skipped`() = runTest {
        val keys = flowOf(
            null,
            aircraft("BAW256", lat = 51.0, seenAt = Instant.parse("2026-11-06T12:00:00Z")),
            null,
        ).routeLookupKeys().toList()

        keys shouldBe listOf("A1B2C3" to "BAW256")
    }
}
