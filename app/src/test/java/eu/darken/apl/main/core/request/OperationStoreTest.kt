package eu.darken.apl.main.core.request

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import eu.darken.apl.main.core.db.AircraftDatabase
import eu.darken.apl.main.core.db.AircraftRoomDb
import eu.darken.apl.main.core.db.PendingOperationEntity
import eu.darken.apl.server.ServerModule
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
import java.time.Instant
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class OperationStoreTest {

    private lateinit var roomDb: AircraftRoomDb
    private lateinit var store: OperationStore

    @Before
    fun setup() {
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

        store = OperationStore(database, TestDispatcherProvider(), ServerModule.serverJson())
    }

    @After
    fun teardown() {
        roomDb.close()
    }

    private suspend fun insert(operationId: String, kind: String, resultJson: String? = null) {
        roomDb.pendingOperations().insert(
            PendingOperationEntity(
                operationId = operationId,
                kind = kind,
                requestJson = "{}",
                ownerIds = """["watch-1"]""",
                createdAt = NOW.toEpochMilli(),
                resultJson = resultJson,
            )
        )
    }

    @Test
    fun `rows of the app's own watch checks and history pages are deleted, live checks stay`() {
        runTest {
            insert("watch", "WATCH", resultJson = "receipt")
            insert("history", "WATCH_HISTORY")
            insert("live", OperationStore.Kind.WATCH_LIVE.name)
            insert("search", OperationStore.Kind.SEARCH.name)

            store.pending(OperationStore.Kind.SEARCH, NOW).map { it.operationId } shouldBe listOf("search")

            roomDb.pendingOperations().getAll().map { it.operationId }.toSet() shouldBe setOf("live", "search")
            store.pending(OperationStore.Kind.WATCH_LIVE, NOW).map { it.operationId } shouldBe listOf("live")
        }
    }

    @Test
    fun `deleting the retired rows again finds nothing to delete`() {
        runTest {
            insert("watch", "WATCH")
            insert("live", OperationStore.Kind.WATCH_LIVE.name)

            store.pending(OperationStore.Kind.WATCH_LIVE, NOW)
            store.pending(OperationStore.Kind.WATCH_LIVE, NOW).map { it.operationId } shouldBe listOf("live")

            roomDb.pendingOperations().getAll().map { it.operationId } shouldBe listOf("live")
        }
    }

    companion object {
        private val NOW = Instant.ofEpochMilli(1_710_000_000_000L)
    }
}
