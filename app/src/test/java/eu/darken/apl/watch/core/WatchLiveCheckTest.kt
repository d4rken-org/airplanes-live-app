package eu.darken.apl.watch.core

import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.main.core.query.BatchResult
import eu.darken.apl.main.core.query.WatchOutcome
import eu.darken.apl.main.core.request.OperationStore
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.api.ServerCodes
import eu.darken.apl.server.api.UsageUpdate
import eu.darken.apl.server.api.WatchDefinition
import eu.darken.apl.watch.core.db.types.BaseWatchEntity
import eu.darken.apl.watch.core.db.types.LocationWatchEntity
import eu.darken.apl.watch.core.types.LocationWatch
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.io.IOException
import java.time.Instant

class WatchLiveCheckTest : BaseTest() {

    private val aircraftRepo = mockk<AircraftRepo>()
    private val operationStore = mockk<OperationStore>(relaxed = true)
    private val gate = WatchRequestGate()
    private val accessRepo = mockk<AccessRepo>(relaxed = true)
    private val serverClock = mockk<ServerClock> { every { now() } returns Instant.ofEpochSecond(1_700_000_000) }

    @BeforeEach
    fun setup() {
        coEvery { operationStore.pending(any(), any()) } returns emptyList()
    }

    private val watch = LocationWatch(
        BaseWatchEntity(
            id = "l1",
            watchType = LocationWatchEntity.TYPE_KEY,
            latitude = 52.5,
            longitude = 13.4,
            radius = 25_000f,
            definitionRevision = 3,
        ),
        LocationWatchEntity(id = "l1", label = "Berlin"),
    )

    private fun create() = WatchLiveCheck(aircraftRepo, operationStore, gate, accessRepo, serverClock)

    @Test
    fun `the watch's definition is checked and the operation completed`() = runTest {
        val chunks = slot<List<AircraftRepo.Chunk<WatchDefinition>>>()
        coEvery { aircraftRepo.checkWatches(capture(chunks), OperationStore.Kind.WATCH_LIVE) } answers {
            gate.isLiveWaiting shouldBe true
            listOf(batch(WatchOutcome.Absent(expiresAt = null)))
        }

        create().check(watch) shouldBe WatchOutcome.Absent(expiresAt = null)

        val chunk = chunks.captured.single()
        chunk.items shouldBe listOf(watch.toDefinition())
        chunk.ownerIds shouldBe listOf("l1")
        chunk.ownerRevisions shouldBe listOf(3L)
        coVerify { operationStore.complete(chunk.operationId!!) }
    }

    @Test
    fun `a failed check leaves no operation for the monitor to replay`() = runTest {
        val chunks = slot<List<AircraftRepo.Chunk<WatchDefinition>>>()
        coEvery { aircraftRepo.checkWatches(capture(chunks), OperationStore.Kind.WATCH_LIVE) } throws IOException("offline")

        shouldThrow<IOException> { create().check(watch) }

        coVerify { operationStore.complete(chunks.captured.single().operationId!!) }
    }

    @Test
    fun `an operation a killed process left behind is retired, not replayed`() = runTest {
        coEvery { operationStore.pending(OperationStore.Kind.WATCH_LIVE, any()) } returns listOf(
            OperationStore.PendingOperation(
                operationId = "left-behind",
                kind = OperationStore.Kind.WATCH_LIVE,
                requestJson = "{}",
                ownerIds = listOf("l1"),
                createdAt = Instant.ofEpochSecond(1_700_000_000),
                resultJson = null,
            )
        )
        coEvery { aircraftRepo.checkWatches(any(), OperationStore.Kind.WATCH_LIVE) } returns
            listOf(batch(WatchOutcome.Absent(expiresAt = null)))

        create().check(watch)

        coVerify { operationStore.complete("left-behind") }
        coVerify(exactly = 0) { aircraftRepo.replayWatchOperation(any()) }
    }

    @Test
    fun `a refusal for the tier or the allowance refreshes the access policy`() = runTest {
        coEvery { aircraftRepo.checkWatches(any(), OperationStore.Kind.WATCH_LIVE) } returns
            listOf(batch(WatchOutcome.Rejected(ServerCodes.DAILY_ALLOWANCE_EXHAUSTED)))

        create().check(watch) shouldBe WatchOutcome.Rejected(ServerCodes.DAILY_ALLOWANCE_EXHAUSTED)

        coVerify { accessRepo.refreshThrottled(any()) }
    }

    private fun batch(outcome: WatchOutcome) = BatchResult(
        operationId = "op",
        replayed = false,
        snapshot = null,
        outcomes = listOf(outcome),
        usage = mockk<UsageUpdate>(relaxed = true),
    )
}
