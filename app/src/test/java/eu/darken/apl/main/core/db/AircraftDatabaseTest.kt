package eu.darken.apl.main.core.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class AircraftDatabaseTest {

    private lateinit var roomDb: AircraftRoomDb

    @Before
    fun setup() {
        roomDb = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AircraftRoomDb::class.java,
        ).build()
    }

    @After
    fun teardown() {
        roomDb.close()
    }

    private fun entity(
        hex: String = "ABC123",
        messageSeenAt: Instant?,
        fetchedAt: Instant = Instant.ofEpochMilli(1_710_000_000_000),
        callsign: String? = null,
    ) = CachedAircraftEntity(
        hex = hex,
        source = "adsb_icao",
        registration = null,
        callsign = callsign,
        operator = null,
        airframe = null,
        description = null,
        squawk = null,
        emergency = null,
        military = false,
        ladd = false,
        pia = false,
        outsideTemp = null,
        altitudeFt = null,
        onGround = null,
        geometricAltitudeFt = null,
        altitudeRate = null,
        groundSpeed = null,
        indicatedAirSpeed = null,
        trackheading = null,
        groundTrack = null,
        location = null,
        messageSeenAt = messageSeenAt,
        positionSeenAt = null,
        fetchedAt = fetchedAt,
    )

    @Test
    fun `an older observation does not replace a newer one`() = runBlocking<Unit> {
        val dao = roomDb.aircraft()
        val newer = Instant.ofEpochMilli(2_000)
        val older = Instant.ofEpochMilli(1_000)

        dao.upsertNewerWins(listOf(entity(messageSeenAt = newer, callsign = "NEW")))
        dao.upsertNewerWins(listOf(entity(messageSeenAt = older, callsign = "OLD")))

        dao.current().first().single().apply {
            callsign shouldBe "NEW"
            messageSeenAt shouldBe newer
        }
    }

    @Test
    fun `a newer observation replaces an older one`() = runBlocking<Unit> {
        val dao = roomDb.aircraft()
        dao.upsertNewerWins(listOf(entity(messageSeenAt = Instant.ofEpochMilli(1_000), callsign = "OLD")))
        dao.upsertNewerWins(listOf(entity(messageSeenAt = Instant.ofEpochMilli(2_000), callsign = "NEW")))

        dao.current().first().single().callsign shouldBe "NEW"
    }

    @Test
    fun `an observation of unknown age does not replace a dated one`() = runBlocking<Unit> {
        val dao = roomDb.aircraft()
        dao.upsertNewerWins(listOf(entity(messageSeenAt = Instant.ofEpochMilli(1_000), callsign = "DATED")))
        dao.upsertNewerWins(listOf(entity(messageSeenAt = null, callsign = "UNKNOWN")))

        dao.current().first().single().callsign shouldBe "DATED"
    }

    @Test
    fun `overlapping writers leave the newest observation`() = runBlocking<Unit> {
        val dao = roomDb.aircraft()
        val newer = Instant.ofEpochMilli(2_000)
        val older = Instant.ofEpochMilli(1_000)

        repeat(50) {
            dao.delete("ABC123")
            listOf(
                async(Dispatchers.IO) { dao.upsertNewerWins(listOf(entity(messageSeenAt = older, callsign = "OLD"))) },
                async(Dispatchers.IO) { dao.upsertNewerWins(listOf(entity(messageSeenAt = newer, callsign = "NEW"))) },
            ).awaitAll()

            dao.current().first().single().callsign shouldBe "NEW"
        }
    }

    @Test
    fun `eviction removes rows fetched before the cutoff`() = runBlocking<Unit> {
        val dao = roomDb.aircraft()
        dao.upsertAll(
            listOf(
                entity(hex = "AAAAAA", messageSeenAt = null, fetchedAt = Instant.ofEpochMilli(1_000)),
                entity(hex = "BBBBBB", messageSeenAt = null, fetchedAt = Instant.ofEpochMilli(5_000)),
            )
        )

        dao.deleteFetchedBefore(3_000) shouldBe 1

        dao.current().first().single().hex shouldBe "BBBBBB"
    }
}
