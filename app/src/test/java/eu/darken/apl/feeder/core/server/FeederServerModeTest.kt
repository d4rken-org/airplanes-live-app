package eu.darken.apl.feeder.core.server

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import eu.darken.apl.common.serialization.SerializationModule
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.access.AccessState
import eu.darken.apl.server.api.Allowance
import eu.darken.apl.server.api.RequestLimits
import eu.darken.apl.server.api.RequestRate
import eu.darken.apl.server.api.TierPolicy
import eu.darken.apl.server.api.Usage
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.io.File
import java.time.Instant
import java.util.UUID

class FeederServerModeTest : BaseTest() {

    private val accessState = MutableStateFlow<AccessState?>(null)
    private val accessRepo = mockk<AccessRepo>(relaxed = true).apply {
        every { state } returns accessState
    }

    private lateinit var storeFile: File
    private lateinit var storeScope: CoroutineScope
    private lateinit var store: FeederServerStore
    private lateinit var mode: FeederServerMode

    @BeforeEach
    fun setup() {
        storeFile = File(IO_TEST_BASEDIR, "feeder-server-mode-${UUID.randomUUID()}.preferences_pb").apply {
            parentFile?.mkdirs()
            delete()
        }
        storeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val dataStore = PreferenceDataStoreFactory.create(scope = storeScope, produceFile = { storeFile })
        store = FeederServerStore(dataStore, SerializationModule().json())
        mode = FeederServerMode(accessRepo, store)
    }

    @AfterEach
    fun teardown() {
        runBlocking { storeScope.coroutineContext.job.cancelAndJoin() }
        storeFile.delete()
    }

    private fun access(installationId: String = INSTALLATION, feederMonitor: Boolean = true) = AccessState(
        installationId = installationId,
        fetchedAt = Instant.EPOCH,
        tier = AccessState.Tier.FEEDER,
        restricted = false,
        allowanceScope = "principal",
        limits = TierPolicy(
            viewingIntervalSeconds = 5,
            viewingBurst = 3,
            viewingPerDay = 25_000,
            searchesPerDay = 25,
            watchesPerDay = 1000,
            maxRadiusNm = 25,
            watchTypes = listOf("hex"),
        ),
        usage = Usage(
            resetsAt = 0,
            viewing = Allowance(25_000, 0, 0, 25_000),
            search = Allowance(25, 0, 0, 25),
            watch = Allowance(1000, 0, 0, 1000),
        ),
        installationRequests = RequestLimits(RequestRate(1.0, 3), RequestRate(1.0, 3), RequestRate(1.0, 3), 3),
        aircraftOperations = if (feederMonitor) listOf(AccessState.OPERATION_FEEDER_MONITOR) else emptyList(),
    )

    private suspend fun both(): Pair<FeederServerMode.State, FeederServerMode.State> = mode.state.first() to mode.current()

    @Test
    fun `without an access there is no server mode`() = runBlocking<Unit> {
        val (flow, current) = both()

        flow shouldBe FeederServerMode.State(access = null, isActive = false)
        current shouldBe flow
    }

    @Test
    fun `an access without feeder monitoring is no server mode, whatever is stored`() = runBlocking<Unit> {
        val access = access(feederMonitor = false)
        accessState.value = access
        store.update { it.copy(installationId = INSTALLATION, cursor = 3) }

        val (flow, current) = both()

        flow shouldBe FeederServerMode.State(access = access, isActive = false)
        current shouldBe flow
    }

    @Test
    fun `feeder monitoring without a stored installation is server mode`() = runBlocking<Unit> {
        val access = access()
        accessState.value = access

        val (flow, current) = both()

        flow shouldBe FeederServerMode.State(access = access, isActive = true, server = FeederServerState())
        current shouldBe flow
    }

    @Test
    fun `feeder monitoring with the state of its installation is server mode and carries it`() = runBlocking<Unit> {
        val access = access()
        accessState.value = access
        val stored = store.update { it.copy(installationId = INSTALLATION, cursor = 3) }

        val (flow, current) = both()

        flow shouldBe FeederServerMode.State(access = access, isActive = true, server = stored)
        current shouldBe flow
    }

    @Test
    fun `the state of another installation is no server mode`() = runBlocking<Unit> {
        val access = access()
        accessState.value = access
        store.update { it.copy(installationId = "other-installation", cursor = 3) }

        val (flow, current) = both()

        flow shouldBe FeederServerMode.State(access = access, isActive = false)
        current shouldBe flow
    }

    companion object {
        private const val INSTALLATION = "installation-a"
    }
}
