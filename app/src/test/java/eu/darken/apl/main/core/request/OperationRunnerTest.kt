package eu.darken.apl.main.core.request

import androidx.test.core.app.ApplicationProvider
import eu.darken.apl.common.MonotonicClock
import androidx.room.Room
import eu.darken.apl.main.core.db.AircraftDatabase
import eu.darken.apl.main.core.db.AircraftRoomDb
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.ServerModule
import eu.darken.apl.server.api.ServerApiException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelper.coroutine.TestDispatcherProvider
import java.io.IOException
import java.time.Instant
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class OperationRunnerTest {

    private val json = ServerModule.serverJson()
    private val monotonicClock = object : MonotonicClock {
        override fun elapsed(): Long = 0L
    }
    private val serverClock = ServerClock(monotonicClock).apply { noteServerTime(NOW_MILLIS) }

    private lateinit var roomDb: AircraftRoomDb
    private lateinit var store: OperationStore
    private lateinit var runner: OperationRunner

    @Before
    fun setup() {
        // Direct executors keep Room on the calling thread, the test drives virtual time
        val direct = Executor { it.run() }
        roomDb = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AircraftRoomDb::class.java,
        )
            .setQueryExecutor(direct)
            .setTransactionExecutor(direct)
            .build()

        val database = mockk<AircraftDatabase>()
        every { database.pendingOperations } returns roomDb.pendingOperations()

        store = OperationStore(database, TestDispatcherProvider(), json)
        runner = OperationRunner(store, serverClock)
    }

    @After
    fun teardown() {
        roomDb.close()
    }

    @Test
    fun `the operation is stored before the send and keeps its receipt until completion`() {
        runTest {
            var pendingDuringSend: OperationStore.PendingOperation? = null

            val result = runner.run(
                kind = OperationStore.Kind.WATCH,
                operationId = OPERATION_ID,
                requestJson = REQUEST_JSON,
                ownerIds = listOf("watch-1"),
                encodeResult = { it },
            ) {
                pendingDuringSend = store.pending(OperationStore.Kind.WATCH, NOW).single()
                "result-body"
            }

            result shouldBe "result-body"
            pendingDuringSend!!.operationId shouldBe OPERATION_ID
            pendingDuringSend!!.ownerIds shouldBe listOf("watch-1")
            pendingDuringSend!!.resultJson.shouldBeNull()

            store.pending(OperationStore.Kind.WATCH, NOW).single().resultJson shouldBe "result-body"

            store.complete(OPERATION_ID)
            store.pending(OperationStore.Kind.WATCH, NOW) shouldBe emptyList()
        }
    }

    @Test
    fun `an operation in progress is retried under the same id`() {
        runTest {
            val sentIds = mutableListOf<String>()

            val result = runner.run(
                kind = OperationStore.Kind.WATCH,
                operationId = OPERATION_ID,
                requestJson = REQUEST_JSON,
                ownerIds = emptyList(),
                encodeResult = { it },
            ) { id ->
                sentIds.add(id)
                if (sentIds.size == 1) {
                    throw ServerApiException(code = "operation_in_progress", status = 409, retryAfterSeconds = 1)
                }
                "result-body"
            }

            result shouldBe "result-body"
            sentIds shouldBe listOf(OPERATION_ID, OPERATION_ID)
        }
    }

    @Test
    fun `a network failure is retried under the same id`() {
        runTest {
            val sentIds = mutableListOf<String>()

            runner.run(
                kind = OperationStore.Kind.SEARCH,
                operationId = OPERATION_ID,
                requestJson = REQUEST_JSON,
                ownerIds = emptyList(),
                encodeResult = { it },
            ) { id ->
                sentIds.add(id)
                if (sentIds.size == 1) throw IOException("offline")
                "result-body"
            }

            sentIds shouldBe listOf(OPERATION_ID, OPERATION_ID)
        }
    }

    @Test
    fun `an expired operation is dropped and reported`() {
        runTest {
            val error = shouldThrow<OperationFailedException> {
                runner.run(
                    kind = OperationStore.Kind.WATCH,
                    operationId = OPERATION_ID,
                    requestJson = REQUEST_JSON,
                    ownerIds = emptyList(),
                    encodeResult = { it: String -> it },
                ) {
                    throw ServerApiException(code = "operation_expired", status = 409)
                }
            }

            error.code shouldBe "operation_expired"
            store.pending(OperationStore.Kind.WATCH, NOW) shouldBe emptyList()
        }
    }

    @Test
    fun `operations past the replay window are discarded`() {
        runTest {
            store.start(
                kind = OperationStore.Kind.WATCH,
                operationId = OPERATION_ID,
                requestJson = REQUEST_JSON,
                ownerIds = emptyList(),
                now = NOW.minusSeconds(6 * 60),
            )

            store.pending(OperationStore.Kind.WATCH, NOW) shouldBe emptyList()
        }
    }

    @Test
    fun `a receipt survives for replay when the result was never applied`() {
        runTest {
            runner.run(
                kind = OperationStore.Kind.WATCH,
                operationId = OPERATION_ID,
                requestJson = REQUEST_JSON,
                ownerIds = listOf("watch-1"),
                encodeResult = { it: String -> it },
            ) { "result-body" }

            // No complete() call, this is what a crash between receipt and application looks like
            store.pending(OperationStore.Kind.WATCH, NOW).single().apply {
                resultJson shouldBe "result-body"
                ownerIds shouldBe listOf("watch-1")
            }
        }
    }

    companion object {
        private const val NOW_MILLIS = 1_710_000_000_000L
        private val NOW = Instant.ofEpochMilli(NOW_MILLIS)
        private const val OPERATION_ID = "5a5c6b2e-3b0a-4a2e-9a0f-000000000001"
        private const val REQUEST_JSON = """{"operationId":"5a5c6b2e-3b0a-4a2e-9a0f-000000000001","watches":[]}"""
    }
}
