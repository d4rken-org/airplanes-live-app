package eu.darken.apl.server.deletion

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.io.File

class DeletionStateStoreTest : BaseTest() {

    private lateinit var storeFile: File
    private lateinit var storeScope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>

    @BeforeEach
    fun setup() {
        storeFile = File(IO_TEST_BASEDIR, "deletion-${storeCounter++}.preferences_pb")
        storeFile.parentFile?.mkdirs()
        storeFile.delete()
        openDataStore()
    }

    @AfterEach
    fun teardown() {
        runBlocking { storeScope.coroutineContext.job.cancelAndJoin() }
        storeFile.delete()
    }

    private fun openDataStore() {
        storeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        dataStore = PreferenceDataStoreFactory.create(scope = storeScope, produceFile = { storeFile })
    }

    /** A DataStore keeps its file claimed until its scope is done, like the app process. */
    private suspend fun restart(): DeletionStateStore {
        storeScope.coroutineContext.job.cancelAndJoin()
        openDataStore()
        return DeletionStateStore(dataStore)
    }

    @Test
    fun `nothing stored reads as none`() = runBlocking<Unit> {
        val store = DeletionStateStore(dataStore)

        store.current() shouldBe DeletionState.NONE
        store.state.first() shouldBe DeletionState.NONE
    }

    @Test
    fun `every state survives a restart`() = runBlocking<Unit> {
        DeletionState.entries.forEach { state ->
            DeletionStateStore(dataStore).set(state)

            val restarted = restart()

            restarted.current() shouldBe state
            restarted.state.first() shouldBe state
        }
    }

    @Test
    fun `setting none clears the stored value`() = runBlocking<Unit> {
        val store = DeletionStateStore(dataStore)
        store.set(DeletionState.WIPE_PENDING)

        store.set(DeletionState.NONE)

        store.current() shouldBe DeletionState.NONE
        dataStore.data.first()[STATE_KEY].shouldBeNull()
    }

    @Test
    fun `the state is stored by name`() = runBlocking<Unit> {
        DeletionStateStore(dataStore).set(DeletionState.REQUEST_PENDING)

        dataStore.data.first()[STATE_KEY] shouldBe "REQUEST_PENDING"
    }

    @Test
    fun `an unknown stored value reads as none`() = runBlocking<Unit> {
        dataStore.edit { it[STATE_KEY] = "SOMETHING_ELSE" }

        DeletionStateStore(dataStore).current() shouldBe DeletionState.NONE
    }

    @Test
    fun `a stored pending wipe keeps the request gate closed`() = runBlocking<Unit> {
        val store = DeletionStateStore(dataStore)
        store.set(DeletionState.WIPE_PENDING)
        val gate = ServerRequestGate(store)

        withTimeoutOrNull(200) { gate.awaitOpen() }.shouldBeNull()

        store.set(DeletionState.NONE)
        withTimeout(5_000) { gate.awaitOpen() }
    }

    companion object {
        private val STATE_KEY = stringPreferencesKey("deletion.state")
        private var storeCounter = 0
    }
}
