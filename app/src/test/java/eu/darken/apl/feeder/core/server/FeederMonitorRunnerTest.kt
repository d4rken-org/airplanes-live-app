package eu.darken.apl.feeder.core.server

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import eu.darken.apl.common.serialization.SerializationModule
import eu.darken.apl.feeder.core.monitor.FeederMonitorNotifications
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.access.AccessState
import eu.darken.apl.server.api.Allowance
import eu.darken.apl.server.api.RequestLimits
import eu.darken.apl.server.api.RequestRate
import eu.darken.apl.server.api.TierPolicy
import eu.darken.apl.server.api.Usage
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.UUID

class FeederMonitorRunnerTest : BaseTest() {

    private val mode = mockk<FeederServerMode>()
    private val sync = mockk<FeederMonitorSync>()
    private val alerts = mockk<FeederServerAlerts>().apply {
        coEvery { deliver() } returns Unit
    }

    private val store = mockk<FeederServerStore>().apply {
        coEvery { current() } returns FeederServerState(installationId = INSTALLATION_A)
    }
    private val notifications = mockk<FeederMonitorNotifications>(relaxed = true)

    private val runner = FeederMonitorRunner(mode, sync, store, alerts, notifications)

    private fun active(isActive: Boolean) {
        coEvery { mode.current() } returns FeederServerMode.State(
            access = access(INSTALLATION_A, feederMonitor = isActive),
            isActive = isActive,
        )
    }

    private val accessState = MutableStateFlow<AccessState?>(null)
    private val accessRepo = mockk<AccessRepo>(relaxed = true).apply {
        every { state } returns accessState
    }

    private lateinit var storeFile: File
    private lateinit var storeScope: CoroutineScope
    private lateinit var realStore: FeederServerStore
    private lateinit var realMode: FeederServerMode
    private lateinit var realRunner: FeederMonitorRunner

    @BeforeEach
    fun setup() {
        storeFile = File(IO_TEST_BASEDIR, "feeder-monitor-runner-${UUID.randomUUID()}.preferences_pb").apply {
            parentFile?.mkdirs()
            delete()
        }
        storeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val dataStore = PreferenceDataStoreFactory.create(scope = storeScope, produceFile = { storeFile })
        realStore = FeederServerStore(dataStore, SerializationModule().json())
        realMode = FeederServerMode(accessRepo, realStore)
        realRunner = FeederMonitorRunner(realMode, sync, realStore, alerts, notifications)
    }

    @AfterEach
    fun teardown() {
        runBlocking { storeScope.coroutineContext.job.cancelAndJoin() }
        storeFile.delete()
    }

    private fun access(installationId: String, feederMonitor: Boolean = true) = AccessState(
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

    @Test
    fun `outside server mode nothing runs`() = runBlocking<Unit> {
        active(false)

        runner.run("test") shouldBe FeederMonitorSync.Result.Inactive

        coVerify(exactly = 0) { sync.run(any(), any()) }
        coVerify(exactly = 0) { alerts.deliver() }
    }

    @Test
    fun `a run syncs, then delivers`() = runBlocking<Unit> {
        active(true)
        val done = FeederMonitorSync.Result.Done(moreWork = true)
        coEvery { sync.run("test", true) } returns done

        runner.run("test", foreground = true) shouldBe done

        coVerifyOrder {
            sync.run("test", true)
            alerts.deliver()
        }
    }

    @Test
    fun `a run that stays with its installation leaves the notifications`() = runBlocking<Unit> {
        active(true)
        coEvery { sync.run(any(), any()) } returns FeederMonitorSync.Result.Done(moreWork = false)

        runner.run("test")

        verify(exactly = 0) { notifications.cancelAllServer() }
    }

    @Test
    fun `an aborted run cancels every server notification before it delivers`() = runBlocking<Unit> {
        active(true)
        coEvery { store.current() } returnsMany listOf(
            FeederServerState(installationId = INSTALLATION_A),
            FeederServerState(),
        )
        coEvery { sync.run(any(), any()) } returns FeederMonitorSync.Result.Aborted

        runner.run("test") shouldBe FeederMonitorSync.Result.Aborted

        coVerifyOrder {
            notifications.cancelAllServer()
            alerts.deliver()
        }
    }

    @Test
    fun `a run that starts over for another installation cancels every server notification`() = runBlocking<Unit> {
        active(true)
        coEvery { store.current() } returnsMany listOf(
            FeederServerState(installationId = INSTALLATION_A),
            FeederServerState(installationId = INSTALLATION_B),
        )
        coEvery { sync.run(any(), any()) } returns FeederMonitorSync.Result.Done(moreWork = false)

        runner.run("test")

        verify(exactly = 1) { notifications.cancelAllServer() }
    }

    @Test
    fun `a failed sync still delivers and passes its exception on`() = runBlocking<Unit> {
        active(true)
        coEvery { sync.run(any(), any()) } throws IOException("offline")

        shouldThrow<IOException> { runner.run("test") }

        coVerify(exactly = 1) { alerts.deliver() }
    }

    @Test
    fun `a failed delivery after a failed sync does not hide the sync's exception`() = runBlocking<Unit> {
        active(true)
        coEvery { sync.run(any(), any()) } throws IOException("offline")
        coEvery { alerts.deliver() } throws IllegalStateException("broken")

        val e = shouldThrow<IOException> { runner.run("test") }

        e.suppressed.single()::class shouldBe IllegalStateException::class
    }

    @Test
    fun `access for another installation runs the sync, which starts over, then cancels and delivers`() = runBlocking<Unit> {
        realStore.update { FeederServerState(installationId = INSTALLATION_A, cursor = 7) }
        accessState.value = access(INSTALLATION_B)
        coEvery { sync.run(any(), any()) } coAnswers {
            realStore.update { FeederServerState(installationId = INSTALLATION_B) }
            FeederMonitorSync.Result.Done(moreWork = false)
        }

        realRunner.run("test") shouldBe FeederMonitorSync.Result.Done(moreWork = false)

        coVerifyOrder {
            sync.run("test", false)
            notifications.cancelAllServer()
            alerts.deliver()
        }
    }

    @Test
    fun `access for another installation does not deliver when the sync fails before starting over`() = runBlocking<Unit> {
        realStore.update { FeederServerState(installationId = INSTALLATION_A, cursor = 7) }
        accessState.value = access(INSTALLATION_B)
        coEvery { sync.run(any(), any()) } throws IOException("offline")

        shouldThrow<IOException> { realRunner.run("test") }

        coVerify(exactly = 1) { sync.run(any(), any()) }
        coVerify(exactly = 0) { alerts.deliver() }
    }

    @Test
    fun `access without feeder monitoring does not run the sync`() = runBlocking<Unit> {
        realStore.update { FeederServerState(installationId = INSTALLATION_A, cursor = 7) }
        accessState.value = access(INSTALLATION_B, feederMonitor = false)

        realRunner.run("test") shouldBe FeederMonitorSync.Result.Inactive

        coVerify(exactly = 0) { sync.run(any(), any()) }
        coVerify(exactly = 0) { alerts.deliver() }
    }

    companion object {
        private const val INSTALLATION_A = "installation-a"
        private const val INSTALLATION_B = "installation-b"
    }
}
