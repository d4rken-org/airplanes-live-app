package eu.darken.apl.ar.core

import android.location.Location
import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.common.compose.preview.FakeAircraft
import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.main.core.query.QuerySnapshot
import eu.darken.apl.main.core.query.ViewingSnapshot
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.access.AccessState
import eu.darken.apl.server.api.Allowance
import eu.darken.apl.server.api.UsageUpdate
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import testhelper.coroutine.TestDispatcherProvider
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class ArAircraftProviderTest {

    private val aircraftRepo = mockk<AircraftRepo>()
    private val arSettings = mockk<ArSettings>(relaxed = true)
    private val accessRepo = mockk<AccessRepo>()
    private val accessState = MutableStateFlow<AccessState?>(null)

    private val viewingState = MutableStateFlow<AircraftRepo.ViewingState?>(null)
    private val locationState = MutableStateFlow<Location?>(null)

    private var elapsed = 0L
    private val serverClock = ServerClock(object : MonotonicClock {
        override fun elapsed(): Long = elapsed
    })

    @Before
    fun setup() {
        elapsed = 0
        serverClock.noteServerTime(SERVER_TIME)
        every { accessRepo.state } returns accessState
        every { aircraftRepo.viewing(any()) } returns viewingState.filterNotNullFlow()
        locationState.value = Location("test").apply {
            latitude = 50.0
            longitude = 8.0
        }
    }

    private fun MutableStateFlow<AircraftRepo.ViewingState?>.filterNotNullFlow() =
        kotlinx.coroutines.flow.flow {
            collect { if (it != null) emit(it) }
        }

    private fun provider() = ArAircraftProvider(
        locationState = locationState,
        aircraftRepo = aircraftRepo,
        arSettings = arSettings,
        accessRepo = accessRepo,
        serverClock = serverClock,
        dispatcherProvider = TestDispatcherProvider(),
        maxRangeNm = 50.0,
    )

    private fun snapshotWith(vararg aircraft: Aircraft) = AircraftRepo.ViewingState.Snapshot(
        ViewingSnapshot(
            aircraft = aircraft.toList(),
            complete = true,
            capped = false,
            totalMatching = aircraft.size,
            snapshot = QuerySnapshot(
                serverTime = Instant.ofEpochMilli(SERVER_TIME),
                receivedAtElapsed = 0,
                sourceTime = Instant.ofEpochMilli(SERVER_TIME),
                fetchedAt = Instant.ofEpochMilli(SERVER_TIME),
                expiresAt = Instant.ofEpochMilli(SERVER_TIME + 120_000),
                complete = true,
                stale = false,
                unpositionedAircraft = 0,
            ),
            usage = UsageUpdate(
                scope = "principal",
                bucket = "VIEWING",
                resetsAt = SERVER_TIME + 3_600_000,
                allowance = Allowance(25000, 1, 0, 24999),
            ),
        )
    )

    private fun moving(positionAgeSec: Long) = FakeAircraft(
        hex = "3C65A3",
        altitudeFt = 30000,
        altitudeRate = 0,
        groundSpeed = 450f,
        groundTrack = 90f,
        location = Location("apl").apply {
            latitude = 50.1
            longitude = 8.1
        },
        messageSeenAt = Instant.ofEpochMilli(SERVER_TIME - positionAgeSec * 1000),
        positionSeenAt = Instant.ofEpochMilli(SERVER_TIME - positionAgeSec * 1000),
        fetchedAt = Instant.ofEpochMilli(SERVER_TIME),
    )

    @Test
    fun `a recent observation is extrapolated`() {
        runTest {
            viewingState.value = snapshotWith(moving(positionAgeSec = 10))

            val result = provider().aircraft.first { it.isNotEmpty() }.single()

            result.positionAgeSec shouldBe 10f
            result.isStale shouldBe false
            result.opacity shouldBe 1f
            (result.interpolatedLon > 8.1) shouldBe true
        }
    }

    @Test
    fun `an aging observation freezes and fades`() {
        runTest {
            viewingState.value = snapshotWith(moving(positionAgeSec = 20))

            val result = provider().aircraft.first { it.isNotEmpty() }.single()

            result.isStale shouldBe true
            (result.opacity < 1f) shouldBe true
            (result.opacity >= 0.3f) shouldBe true
            result.interpolatedLon shouldBe 8.1
        }
    }

    @Test
    fun `an observation past the hide age disappears`() {
        runTest {
            viewingState.value = snapshotWith(moving(positionAgeSec = 31))

            provider().aircraft.first() shouldBe emptyList()
        }
    }

    @Test
    fun `an observation without a position age is not shown`() {
        runTest {
            viewingState.value = snapshotWith(
                FakeAircraft(
                    hex = "3C65A3",
                    location = Location("apl").apply {
                        latitude = 50.1
                        longitude = 8.1
                    },
                    positionSeenAt = null,
                )
            )

            provider().aircraft.first() shouldBe emptyList()
        }
    }

    @Test
    fun `redelivering the same snapshot does not rejuvenate an observation`() {
        runTest {
            val aircraft = moving(positionAgeSec = 29)
            viewingState.value = snapshotWith(aircraft)
            val provider = provider()
            provider.aircraft.first { it.isNotEmpty() }.shouldNotBeNull()

            // Server time moved on, the observation timestamp did not
            elapsed += 5_000
            viewingState.value = snapshotWith(aircraft)

            provider.aircraft.first() shouldBe emptyList()
        }
    }

    companion object {
        private const val SERVER_TIME = 1_710_000_000_000L
    }
}
