package eu.darken.apl.main.core.db

import android.location.Location
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.kotest.assertions.withClue
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
        location: Location? = null,
        positionSeenAt: Instant? = null,
        groundTrack: Float? = null,
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
        groundTrack = groundTrack,
        location = location,
        messageSeenAt = messageSeenAt,
        positionSeenAt = positionSeenAt,
        fetchedAt = fetchedAt,
    )

    private fun location(latitude: Double, longitude: Double) = Location("test").apply {
        this.latitude = latitude
        this.longitude = longitude
    }

    private fun stored(
        messageSeenAt: Instant = Instant.ofEpochMilli(1_000),
        withPosition: Boolean = true,
    ) = entity(
        messageSeenAt = messageSeenAt,
        callsign = "CACHED",
        location = if (withPosition) location(50.0, 8.0) else null,
        positionSeenAt = if (withPosition) Instant.ofEpochMilli(900) else null,
        groundTrack = if (withPosition) 90f else null,
    )

    private fun CachedAircraftEntity.shouldHavePosition(
        latitude: Double,
        longitude: Double,
        positionSeenAt: Instant,
        groundTrack: Float?,
    ) {
        location!!.latitude shouldBe latitude
        location!!.longitude shouldBe longitude
        this.positionSeenAt shouldBe positionSeenAt
        this.groundTrack shouldBe groundTrack
    }

    private fun CachedAircraftEntity.shouldHaveStoredPosition() =
        shouldHavePosition(50.0, 8.0, Instant.ofEpochMilli(900), 90f)

    /** Runs [block] once per upsert, each on an empty cache, with that upsert seeding and writing. */
    private fun forEachUpsert(
        block: suspend (upsert: suspend (CachedAircraftEntity) -> Unit) -> Unit,
    ) = runBlocking<Unit> {
        val dao = roomDb.aircraft()
        val newerWins: suspend (CachedAircraftEntity) -> Unit = { dao.upsertNewerWins(listOf(it)) }
        val keepingReference: suspend (CachedAircraftEntity) -> Unit = { dao.upsertKeepingReference(listOf(it)) }
        listOf("upsertNewerWins" to newerWins, "upsertKeepingReference" to keepingReference).forEach { (name, upsert) ->
            dao.delete("ABC123")
            withClue(name) { block(upsert) }
        }
    }

    private suspend fun cached() = roomDb.aircraft().current().first().single()

    @Test
    fun `a newer observation without position keeps the cached position`() = forEachUpsert { upsert ->
        upsert(stored())

        upsert(entity(messageSeenAt = Instant.ofEpochMilli(2_000), callsign = "NEW"))

        cached().apply {
            callsign shouldBe "NEW"
            messageSeenAt shouldBe Instant.ofEpochMilli(2_000)
            shouldHaveStoredPosition()
        }
    }

    @Test
    fun `a newer position replaces the cached one including the ground track`() = forEachUpsert { upsert ->
        upsert(stored())

        upsert(
            entity(
                messageSeenAt = Instant.ofEpochMilli(2_000),
                location = location(51.0, 9.0),
                positionSeenAt = Instant.ofEpochMilli(1_900),
                groundTrack = 180f,
            )
        )
        cached().shouldHavePosition(51.0, 9.0, Instant.ofEpochMilli(1_900), 180f)

        upsert(
            entity(
                messageSeenAt = Instant.ofEpochMilli(3_000),
                location = location(52.0, 10.0),
                positionSeenAt = Instant.ofEpochMilli(2_900),
                groundTrack = null,
            )
        )
        cached().shouldHavePosition(52.0, 10.0, Instant.ofEpochMilli(2_900), null)
    }

    @Test
    fun `a position as old as the cached one replaces it`() = forEachUpsert { upsert ->
        upsert(stored())

        upsert(
            entity(
                messageSeenAt = Instant.ofEpochMilli(2_000),
                location = location(51.0, 9.0),
                positionSeenAt = Instant.ofEpochMilli(900),
                groundTrack = 180f,
            )
        )

        cached().shouldHavePosition(51.0, 9.0, Instant.ofEpochMilli(900), 180f)
    }

    @Test
    fun `a newer observation with an older position keeps the cached position`() = forEachUpsert { upsert ->
        upsert(stored())

        upsert(
            entity(
                messageSeenAt = Instant.ofEpochMilli(2_000),
                callsign = "NEW",
                location = location(51.0, 9.0),
                positionSeenAt = Instant.ofEpochMilli(800),
                groundTrack = 180f,
            )
        )

        cached().apply {
            callsign shouldBe "NEW"
            messageSeenAt shouldBe Instant.ofEpochMilli(2_000)
            shouldHaveStoredPosition()
        }
    }

    @Test
    fun `an incomplete position keeps the cached position`() = forEachUpsert { upsert ->
        upsert(stored())

        upsert(
            entity(
                messageSeenAt = Instant.ofEpochMilli(2_000),
                location = location(51.0, 9.0),
                positionSeenAt = null,
                groundTrack = 180f,
            )
        )
        cached().shouldHaveStoredPosition()

        upsert(
            entity(
                messageSeenAt = Instant.ofEpochMilli(3_000),
                location = null,
                positionSeenAt = Instant.ofEpochMilli(2_900),
                groundTrack = 180f,
            )
        )
        cached().shouldHaveStoredPosition()
    }

    @Test
    fun `a position is written when none is cached`() = forEachUpsert { upsert ->
        upsert(stored(withPosition = false))

        upsert(
            entity(
                messageSeenAt = Instant.ofEpochMilli(2_000),
                location = location(51.0, 9.0),
                positionSeenAt = Instant.ofEpochMilli(1_900),
                groundTrack = 180f,
            )
        )

        cached().shouldHavePosition(51.0, 9.0, Instant.ofEpochMilli(1_900), 180f)
    }

    @Test
    fun `no position stays no position`() = forEachUpsert { upsert ->
        upsert(stored(withPosition = false))

        upsert(entity(messageSeenAt = Instant.ofEpochMilli(2_000), callsign = "NEW"))

        cached().apply {
            callsign shouldBe "NEW"
            location shouldBe null
            positionSeenAt shouldBe null
        }
    }

    @Test
    fun `an older observation changes neither position nor other fields`() = forEachUpsert { upsert ->
        upsert(stored(messageSeenAt = Instant.ofEpochMilli(2_000)))

        upsert(
            entity(
                messageSeenAt = Instant.ofEpochMilli(1_000),
                callsign = "STALE",
                location = location(51.0, 9.0),
                positionSeenAt = Instant.ofEpochMilli(950),
                groundTrack = 180f,
            )
        )
        upsert(entity(messageSeenAt = Instant.ofEpochMilli(1_000), callsign = "STALE"))

        cached().apply {
            callsign shouldBe "CACHED"
            messageSeenAt shouldBe Instant.ofEpochMilli(2_000)
            shouldHaveStoredPosition()
        }
    }

    @Test
    fun `a merged observation keeps reference fields it lacks and clears the moment`() = runBlocking<Unit> {
        val dao = roomDb.aircraft()
        dao.upsertNewerWins(
            listOf(
                entity(messageSeenAt = Instant.ofEpochMilli(1_000), callsign = "OLD").copy(
                    registration = "D-AIZZ",
                    operator = "Lufthansa",
                    airframe = "A320",
                    description = "AIRBUS A-320",
                    squawk = "7700",
                    emergency = "general",
                )
            )
        )

        dao.upsertKeepingReference(listOf(entity(messageSeenAt = Instant.ofEpochMilli(2_000), callsign = "NEW")))

        dao.current().first().single().apply {
            callsign shouldBe "NEW"
            registration shouldBe "D-AIZZ"
            operator shouldBe "Lufthansa"
            airframe shouldBe "A320"
            description shouldBe "AIRBUS A-320"
            squawk shouldBe null
            emergency shouldBe null
        }
    }

    @Test
    fun `a merged observation that is older changes nothing`() = runBlocking<Unit> {
        val dao = roomDb.aircraft()
        dao.upsertNewerWins(listOf(entity(messageSeenAt = Instant.ofEpochMilli(2_000), callsign = "NEW")))

        dao.upsertKeepingReference(
            listOf(entity(messageSeenAt = Instant.ofEpochMilli(1_000), callsign = "OLD").copy(registration = "D-AIZZ"))
        )

        dao.current().first().single().apply {
            callsign shouldBe "NEW"
            registration shouldBe null
        }
    }

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
