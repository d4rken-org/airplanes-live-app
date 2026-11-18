package eu.darken.apl.feeder.core.server

import android.app.NotificationManager
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
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
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.time.Duration
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class FeederServerAlertsNotificationsOffTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val notifications = FeederMonitorNotifications(context, notificationManager)
    private val shadow = shadowOf(notificationManager)

    private val json = SerializationModule().json()
    private val serverClock = ServerClock(object : MonotonicClock {
        override fun elapsed(): Long = 0L
    }).apply { noteServerTime(NOW) }

    private lateinit var appScope: CoroutineScope
    private lateinit var storeScope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: FeederServerStore
    private lateinit var feederGroup: DataStoreValue<FeederGroup>
    private lateinit var notifyProblems: DataStoreValue<Boolean>
    private val feederSettings = mockk<FeederSettings>()
    private val serverMode = mockk<FeederServerMode>().apply {
        coEvery { current() } returns FeederServerMode.State(access = null, isActive = true)
    }

    @Before
    fun setup() {
        appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        storeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val storeFile = File(tmp.root, "feeder-server-alerts.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(scope = storeScope, produceFile = { storeFile })
        store = FeederServerStore(dataStore, json)
        feederGroup = dataStore.createValue("feeder.group", FeederGroup(), json)
        notifyProblems = dataStore.createValue("feeder.notify.problems", true)
        every { feederSettings.feederGroup } returns feederGroup
        every { feederSettings.notifyProblems } returns notifyProblems
    }

    @After
    fun teardown() {
        runBlocking {
            appScope.coroutineContext.job.cancelAndJoin()
            storeScope.coroutineContext.job.cancelAndJoin()
        }
    }

    private fun alerts() = FeederServerAlerts(appScope, store, feederSettings, notifications, serverClock, serverMode)

    private fun offlineNotification() =
        shadow.getNotification(FeederMonitorNotifications.offlineTag(ID_1), FeederMonitorNotifications.SERVER_OFFLINE_ID)

    private suspend fun configureFeeder() {
        feederGroup.value(
            FeederGroup(
                setOf(
                    FeederConfig(
                        receiverId = ID_1,
                        addedAt = Instant.ofEpochSecond(1_700_000_000),
                        label = "Roof",
                        offlineCheckTimeout = Duration.ofHours(48),
                    )
                )
            )
        )
    }

    private fun monitor(health: String, healthSince: Long) = FeederMonitorEntry(
        feederId = ID_1,
        monitorId = MONITOR_1,
        offlineAfterMinutes = 2880,
        status = "active",
        createdAt = 0,
        updatedAt = 0,
        health = health,
        healthSince = healthSince,
        eventSeq = 1,
    )

    private suspend fun setMonitor(health: String, healthSince: Long) {
        store.update { it.copy(monitors = mapOf(ID_1 to monitor(health, healthSince))) }
    }

    @Test
    fun `an outage that starts before notifications were ever allowed is posted once they are`() = runBlocking<Unit> {
        configureFeeder()
        store.update {
            FeederServerState(
                installationId = INSTALLATION,
                cursor = 10,
                monitors = mapOf(ID_1 to monitor("online", ONLINE_SINCE)),
                notifiedInitialized = false,
            )
        }
        val alerts = alerts()

        shadow.setNotificationsEnabled(false)
        alerts.deliver()

        setMonitor("offline", OFFLINE_SINCE)
        alerts.deliver()

        // Robolectric 4.11 keeps what is posted while notifications are off, Android drops it
        withClue("nothing posted while notifications are off") { shadow.size() shouldBe 0 }

        shadow.setNotificationsEnabled(true)
        alerts.deliver()

        withClue("outage that started while notifications were never allowed is posted") {
            offlineNotification().shouldNotBeNull()
        }
        val entry = store.current().notified[ID_1].shouldNotBeNull()
        entry.offline shouldBe true
        entry.offlineSince shouldBe OFFLINE_SINCE
    }

    @Test
    fun `an outage that ends and starts again while notifications are off is posted when they are back on`() =
        runBlocking<Unit> {
            configureFeeder()
            store.update {
                FeederServerState(
                    installationId = INSTALLATION,
                    cursor = 10,
                    monitors = mapOf(ID_1 to monitor("offline", T1)),
                    notified = mapOf(ID_1 to Notified(MONITOR_1, offline = true, offlineSince = T1)),
                    notifiedInitialized = true,
                )
            }
            val alerts = alerts()

            shadow.setNotificationsEnabled(false)
            setMonitor("online", ONLINE_SINCE)
            alerts.deliver()

            setMonitor("offline", T2)
            alerts.deliver()

            // Robolectric 4.11 keeps what is posted while notifications are off, Android drops it
            withClue("nothing posted while notifications are off") { shadow.size() shouldBe 0 }

            shadow.setNotificationsEnabled(true)
            alerts.deliver()

            withClue("second outage posted after notifications are back on") {
                offlineNotification().shouldNotBeNull()
            }
            withClue("recorded outage is the current one") {
                store.current().notified[ID_1]?.offlineSince shouldBe T2
            }
        }

    companion object {
        private const val INSTALLATION = "installation-a"
        private const val ID_1 = "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d"
        private const val MONITOR_1 = "monitor-1"
        private const val T1 = 1_760_000_000_000L
        private const val ONLINE_SINCE = T1 + 3_600_000L
        private const val T2 = T1 + 2 * 3_600_000L
        private const val OFFLINE_SINCE = T2
        private const val NOW = T1 + 3 * 3_600_000L
    }
}
