package eu.darken.apl.main.core

import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.main.core.db.AircraftDatabase
import eu.darken.apl.main.core.request.OperationRunner
import eu.darken.apl.main.core.request.OperationStore
import eu.darken.apl.main.core.request.RequestCoordinator
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.ServerModule
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.api.Allowance
import eu.darken.apl.server.api.BatchResponse
import eu.darken.apl.server.api.ServerEndpoint
import eu.darken.apl.server.api.UsageUpdate
import eu.darken.apl.server.session.SessionManager
import io.kotest.matchers.longs.shouldBeInRange
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import testhelper.coroutine.TestDispatcherProvider
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class AircraftRepoReplayTest {

    private val monotonicClock = object : MonotonicClock {
        override fun elapsed(): Long = 0L
    }
    private val serverClock = ServerClock(monotonicClock)
    private val json = ServerModule.serverJson()

    private fun createRepo(): AircraftRepo {
        val database = mockk<AircraftDatabase>(relaxed = true)
        val store = OperationStore(database, TestDispatcherProvider(), json)
        val accessRepo = mockk<AccessRepo>(relaxed = true)
        return AircraftRepo(
            appScope = CoroutineScope(Dispatchers.Unconfined),
            aircraftDatabase = database,
            endpoint = mockk<ServerEndpoint>(),
            sessionManager = mockk<SessionManager>(),
            accessRepo = accessRepo,
            requestCoordinator = RequestCoordinator(accessRepo, monotonicClock),
            operationRunner = OperationRunner(store, serverClock),
            operationStore = store,
            serverClock = serverClock,
            json = json,
        )
    }

    @Test
    fun `applying a stored receipt does not rewind the server clock`() {
        runTest {
            serverClock.noteServerTime(SERVER_TIME)
            val stored = BatchResponse(
                operationId = OPERATION_ID,
                serverTime = SERVER_TIME - 240_000,
                completedAt = SERVER_TIME - 240_000,
                operationExpiresAt = SERVER_TIME + 60_000,
                outcomes = emptyList(),
                aircraft = emptyList(),
                usage = UsageUpdate("principal", "WATCH", SERVER_TIME + 3_600_000, Allowance(1000, 1, 0, 999)),
            )
            val pending = OperationStore.PendingOperation(
                operationId = OPERATION_ID,
                kind = OperationStore.Kind.WATCH,
                requestJson = REQUEST_JSON,
                ownerIds = listOf("watch-1"),
                createdAt = Instant.ofEpochMilli(SERVER_TIME - 240_000),
                resultJson = json.encodeToString(BatchResponse.serializer(), stored),
            )

            createRepo().replayWatchOperation(pending)

            serverClock.now().toEpochMilli() shouldBeInRange (SERVER_TIME - 1_000)..(SERVER_TIME + 1_000)
        }
    }

    companion object {
        private const val SERVER_TIME = 1_710_000_000_000L
        private const val OPERATION_ID = "5a5c6b2e-3b0a-4a2e-9a0f-000000000001"
        private const val REQUEST_JSON = """{"operationId":"5a5c6b2e-3b0a-4a2e-9a0f-000000000001","watches":[]}"""
    }
}
