package eu.darken.apl.watch.core.types

import android.location.Location
import eu.darken.apl.common.compose.preview.FakeAircraft
import eu.darken.apl.watch.core.db.types.BaseWatchEntity
import eu.darken.apl.watch.core.db.types.LocationWatchEntity
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant

// Matching measures with android.location.Location
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class LocationWatchMatchTest {

    private val now = Instant.parse("2026-11-05T12:00:00Z")

    private val watch = LocationWatch(
        BaseWatchEntity(
            id = "zone",
            watchType = LocationWatchEntity.TYPE_KEY,
            latitude = 50.0,
            longitude = 8.0,
            radius = 10_000f,
        ),
        LocationWatchEntity(id = "zone", label = "FRA"),
    )

    private fun aircraftInside(positionSeenAt: Instant) = FakeAircraft(
        location = Location("test").apply {
            latitude = 50.01
            longitude = 8.0
        },
        messageSeenAt = now,
        positionSeenAt = positionSeenAt,
    )

    @Test
    fun `an aircraft inside the radius matches with a current position`() {
        watch.matches(aircraftInside(positionSeenAt = now.minusSeconds(10))) shouldBe true
    }

    @Test
    fun `a position kept from long before the last message does not match`() {
        watch.matches(aircraftInside(positionSeenAt = now.minus(Duration.ofHours(2)))) shouldBe false
    }
}
