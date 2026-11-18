package eu.darken.apl.feeder.core.server

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.common.datastore.DataStoreValue
import eu.darken.apl.common.datastore.createValue
import eu.darken.apl.common.datastore.value
import eu.darken.apl.common.serialization.SerializationModule
import eu.darken.apl.feeder.core.config.FeederConfig
import eu.darken.apl.feeder.core.config.FeederGroup
import eu.darken.apl.feeder.core.config.FeederSettings
import eu.darken.apl.feeder.core.monitor.FeederMonitorNotifications
import eu.darken.apl.feeder.core.server.FeederServerState.Notified
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.api.FeederMonitorEntry
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.io.File
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.util.UUID

class FeederServerAlertsTest : BaseTest() {

    private val json = SerializationModule().json()
    private val serverClock = ServerClock(object : MonotonicClock {
        override fun elapsed(): Long = 0L
    }).apply { noteServerTime(NOW) }

    private lateinit var storeFile: File
    private lateinit var storeScope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: FeederServerStore
    private lateinit var feederGroup: DataStoreValue<FeederGroup>
    private lateinit var notifyProblems: DataStoreValue<Boolean>
    private val feederSettings = mockk<FeederSettings>()
    private val notifications = mockk<FeederMonitorNotifications>(relaxed = true)
    private lateinit var appScope: CoroutineScope

    private val serverMode = mockk<FeederServerMode>().apply {
        coEvery { current() } returns ACTIVE
    }

    @BeforeEach
    fun setup() {
        appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        storeFile = File(IO_TEST_BASEDIR, "feeder-server-alerts-${UUID.randomUUID()}.preferences_pb").apply {
            parentFile?.mkdirs()
            delete()
        }
        storeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        dataStore = PreferenceDataStoreFactory.create(scope = storeScope, produceFile = { storeFile })
        store = FeederServerStore(dataStore, json)
        feederGroup = dataStore.createValue("feeder.group", FeederGroup(), json)
        notifyProblems = dataStore.createValue("feeder.notify.problems", true)
        every { feederSettings.feederGroup } returns feederGroup
        every { feederSettings.notifyProblems } returns notifyProblems
    }

    @AfterEach
    fun teardown() {
        runBlocking {
            appScope.coroutineContext.job.cancelAndJoin()
            storeScope.coroutineContext.job.cancelAndJoin()
        }
        storeFile.delete()
    }

    private fun alerts(store: FeederServerStore = this.store) =
        FeederServerAlerts(appScope, store, feederSettings, notifications, serverClock, serverMode)

    private fun started() = alerts().also { it.setup() }

    /** Waits until the settings trigger made [count] delivery attempts in total. */
    private fun attempts(count: Int) = coVerify(exactly = count, timeout = TIMEOUT) { serverMode.current() }

    private suspend fun notifyAbout(vararg categories: FeederProblemCategory) = feederGroup.update { group ->
        group.copy(
            configs = group.configs.map { config ->
                FeederProblemCategory.entries.fold(config) { c, category -> c.withProblemCategory(category, category in categories) }
            }.toSet()
        )
    }

    /** A shown problems notification of two problems in different categories, both on. */
    private suspend fun troubled() {
        feederGroup.value(FeederGroup(setOf(config())))
        stored(
            monitor("online", ONLINE_SINCE, problems = listOf("deaf", "flapping")),
            Notified(MONITOR_1, problems = setOf("deaf", "flapping")),
        )
    }

    private fun monitor(health: String, healthSince: Long, problems: List<String> = emptyList()) = FeederMonitorEntry(
        feederId = ID_1,
        monitorId = MONITOR_1,
        offlineAfterMinutes = 2880,
        status = "active",
        createdAt = 0,
        updatedAt = 0,
        health = health,
        healthSince = healthSince,
        problems = problems,
        eventSeq = 1,
    )

    private fun config(receiverId: String = ID_1, order: Long = 0, snoozedAt: Instant? = null) = FeederConfig(
        receiverId = receiverId,
        addedAt = Instant.ofEpochSecond(1_700_000_000 + order),
        label = "Roof",
        offlineCheckTimeout = Duration.ofHours(48),
        offlineCheckSnoozedAt = snoozedAt,
    )

    private suspend fun stored(monitor: FeederMonitorEntry, notified: Notified = Notified(MONITOR_1), initialized: Boolean = true) =
        store.update {
            FeederServerState(
                installationId = INSTALLATION,
                cursor = 10,
                monitors = mapOf(ID_1 to monitor),
                notified = mapOf(ID_1 to notified),
                notifiedInitialized = initialized,
            )
        }

    @Test
    fun `a run cut short between posting and storing posts the same notification again and ends consistent`() =
        runBlocking<Unit> {
            feederGroup.value(FeederGroup(setOf(config())))
            stored(monitor("offline", OFFLINE_SINCE))

            val failingDataStore = WriteFailingDataStore(dataStore).apply { failWrites = true }
            val failing = FeederServerStore(failingDataStore, json)

            shouldThrow<IOException> { alerts(failing).deliver() }
            verify(exactly = 1) { notifications.notifyServerOffline(ID_1, "Roof", OFFLINE_SINCE) }
            store.current().notified.getValue(ID_1).offline shouldBe false

            failingDataStore.failWrites = false
            alerts(failing).deliver()
            verify(exactly = 2) { notifications.notifyServerOffline(ID_1, "Roof", OFFLINE_SINCE) }
            store.current().notified.getValue(ID_1) shouldBe Notified(MONITOR_1, offline = true, offlineSince = OFFLINE_SINCE)

            alerts(failing).deliver()
            verify(exactly = 2) { notifications.notifyServerOffline(any(), any(), any()) }
        }

    @Test
    fun `the first pass records the state without posting`() = runBlocking<Unit> {
        feederGroup.value(FeederGroup(setOf(config())))
        stored(monitor("offline", OFFLINE_SINCE), initialized = false)

        alerts().deliver()

        verify(exactly = 0) { notifications.notifyServerOffline(any(), any(), any()) }
        with(store.current()) {
            notifiedInitialized shouldBe true
            notified.getValue(ID_1).offline shouldBe true
        }
    }

    @Test
    fun `nothing happens without server state`() = runBlocking<Unit> {
        feederGroup.value(FeederGroup(setOf(config())))

        alerts().deliver()

        verify(exactly = 0) { notifications.notifyServerOffline(any(), any(), any()) }
        store.current() shouldBe FeederServerState()
    }

    @Test
    fun `problem alerts follow their setting`() = runBlocking<Unit> {
        feederGroup.value(FeederGroup(setOf(config())))
        stored(monitor("online", ONLINE_SINCE, problems = listOf("deaf")))
        notifyProblems.value(false)

        alerts().deliver()
        verify(exactly = 0) { notifications.notifyServerProblems(any(), any(), any(), any()) }

        notifyProblems.value(true)
        alerts().deliver()
        verify(exactly = 1) { notifications.notifyServerProblems(ID_1, "Roof", listOf("deaf"), false) }
    }

    @Test
    fun `a recovery while snoozed clears the snooze of every variant of the feeder`() = runBlocking<Unit> {
        feederGroup.value(
            FeederGroup(
                setOf(
                    config(snoozedAt = SNOOZED_AT),
                    config(ID_1.uppercase(), order = 1, snoozedAt = SNOOZED_AT),
                    config(ID_2, order = 2, snoozedAt = SNOOZED_AT),
                )
            )
        )
        stored(monitor("online", ONLINE_SINCE), Notified(MONITOR_1, offline = true, offlineSince = OFFLINE_SINCE))

        alerts().deliver()

        verify { notifications.cancelServerOffline(ID_1) }
        verify(exactly = 0) { notifications.notifyServerRecovered(any(), any(), any()) }
        val configs = feederGroup.value().configs.associateBy { it.receiverId }
        configs.getValue(ID_1).offlineCheckSnoozedAt.shouldBeNull()
        configs.getValue(ID_1.uppercase()).offlineCheckSnoozedAt.shouldBeNull()
        configs.getValue(ID_2).offlineCheckSnoozedAt shouldBe SNOOZED_AT
        store.current().notified.getValue(ID_1).offline shouldBe false
    }

    @Test
    fun `snoozing sets every variant of the feeder and cancels both of its notifications`() = runBlocking<Unit> {
        feederGroup.value(FeederGroup(setOf(config(), config(ID_1.uppercase(), order = 1), config(ID_2, order = 2))))

        alerts().snooze(ID_1)

        val configs = feederGroup.value().configs.associateBy { it.receiverId }
        configs.getValue(ID_1).offlineCheckSnoozedAt.shouldNotBeNull()
        configs.getValue(ID_1.uppercase()).offlineCheckSnoozedAt.shouldNotBeNull()
        configs.getValue(ID_2).offlineCheckSnoozedAt.shouldBeNull()
        verify { notifications.cancelServer(ID_1) }
    }

    @Test
    fun `a category change updates and then cancels the shown problems notification right away`() = runBlocking<Unit> {
        troubled()
        started()
        attempts(1)

        notifyAbout(FeederProblemCategory.MESSAGE_RATE)
        verify(timeout = TIMEOUT) { notifications.notifyServerProblems(ID_1, "Roof", listOf("deaf"), true) }

        notifyAbout()
        verify(timeout = TIMEOUT) { notifications.cancelServerProblems(ID_1) }
    }

    @Test
    fun `turning problem alerts off cancels the shown problems notification right away`() = runBlocking<Unit> {
        troubled()
        started()
        attempts(1)

        notifyProblems.value(false)

        verify(timeout = TIMEOUT) { notifications.cancelServerProblems(ID_1) }
    }

    @Test
    fun `a failed delivery leaves the trigger running for the next change`() = runBlocking<Unit> {
        troubled()
        var failures = 1
        every { notifications.notifyServerProblems(any(), any(), any(), any()) } answers {
            if (failures-- > 0) throw IllegalStateException("posting failed")
        }
        started()
        attempts(1)

        notifyAbout(FeederProblemCategory.MESSAGE_RATE)
        verify(exactly = 1, timeout = TIMEOUT) { notifications.notifyServerProblems(ID_1, "Roof", listOf("deaf"), true) }

        notifyAbout(FeederProblemCategory.MESSAGE_RATE, FeederProblemCategory.MLAT)
        verify(exactly = 2, timeout = TIMEOUT) { notifications.notifyServerProblems(ID_1, "Roof", listOf("deaf"), true) }
    }

    @Test
    fun `a category change stored before the start is delivered on start`() = runBlocking<Unit> {
        troubled()
        notifyAbout(FeederProblemCategory.MESSAGE_RATE)

        started()

        verify(timeout = TIMEOUT) { notifications.notifyServerProblems(ID_1, "Roof", listOf("deaf"), true) }
    }

    @Test
    fun `a snooze alone triggers no delivery`() = runBlocking<Unit> {
        troubled()
        started()
        attempts(1)

        feederGroup.update { group ->
            group.copy(configs = group.configs.map { it.copy(offlineCheckSnoozedAt = SNOOZED_WHILE_ONLINE) }.toSet())
        }
        // Delivered in order after a delivery for the snooze, had there been one
        notifyAbout(FeederProblemCategory.MESSAGE_RATE)
        verify(timeout = TIMEOUT) { notifications.cancelServerProblems(ID_1) }

        // A delivery for the snooze would have made it 3
        attempts(2)
    }

    @Test
    fun `nothing is delivered while server mode is inactive`() = runBlocking<Unit> {
        troubled()
        val inactive = FeederServerMode.State(access = null, isActive = false)
        coEvery { serverMode.current() } returnsMany listOf(inactive, inactive, ACTIVE)
        started()
        attempts(1)

        notifyAbout(FeederProblemCategory.MESSAGE_RATE)
        attempts(2)

        // Delivered in order after a delivery while inactive, had there been one
        notifyAbout()
        verify(timeout = TIMEOUT) { notifications.cancelServerProblems(ID_1) }

        verify(exactly = 0) { notifications.notifyServerProblems(any(), any(), any(), any()) }
        verify(exactly = 1) { notifications.cancelServerProblems(any()) }
    }

    private class WriteFailingDataStore(private val delegate: DataStore<Preferences>) : DataStore<Preferences> {
        var failWrites = false

        override val data: Flow<Preferences> get() = delegate.data

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            if (failWrites) throw IOException("disk full")
            return delegate.updateData(transform)
        }
    }

    companion object {
        private const val INSTALLATION = "installation-a"
        private const val ID_1 = "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d"
        private const val ID_2 = "11111111-2222-4333-8444-555555555555"
        private const val MONITOR_1 = "monitor-1"
        private const val OFFLINE_SINCE = 1_760_000_000_000L
        private const val ONLINE_SINCE = OFFLINE_SINCE + 3 * 3_600_000L
        private const val NOW = OFFLINE_SINCE + 5 * 3_600_000L
        private val SNOOZED_AT: Instant = Instant.ofEpochMilli(OFFLINE_SINCE + 60_000L)
        private val SNOOZED_WHILE_ONLINE: Instant = Instant.ofEpochMilli(ONLINE_SINCE + 60_000L)
        private const val TIMEOUT = 5_000L
        private val ACTIVE = FeederServerMode.State(access = null, isActive = true)
    }
}
