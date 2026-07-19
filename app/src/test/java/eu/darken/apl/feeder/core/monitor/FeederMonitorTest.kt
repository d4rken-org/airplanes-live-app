package eu.darken.apl.feeder.core.monitor

import eu.darken.apl.common.datastore.DataStoreValue
import eu.darken.apl.feeder.core.Feeder
import eu.darken.apl.feeder.core.FeederRepo
import eu.darken.apl.feeder.core.FeederStatus
import eu.darken.apl.feeder.core.config.FeederSettings
import eu.darken.apl.feeder.core.stats.FeederStatsRepo
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.io.IOException
import java.time.Duration

class FeederMonitorTest : BaseTest() {

    private val loggedIn = MutableStateFlow(true)
    private lateinit var feederRepo: FeederRepo
    private lateinit var statsRepo: FeederStatsRepo
    private lateinit var notifications: FeederMonitorNotifications

    private lateinit var monitorStateFlow: MutableStateFlow<FeederMonitorState>
    private lateinit var mutedFlow: MutableStateFlow<Set<String>>
    private lateinit var healthEnabledFlow: MutableStateFlow<Boolean>

    private fun <T> fakeValue(backing: MutableStateFlow<T>): DataStoreValue<T> =
        mockk<DataStoreValue<T>> {
            every { flow } returns backing
            coEvery { update(any()) } answers {
                val fn = firstArg<(T) -> T?>()
                val old = backing.value

                @Suppress("UNCHECKED_CAST")
                val new = fn(old) as T
                backing.value = new
                DataStoreValue.Updated(old, new)
            }
        }

    private fun createMonitor(
        scope: CoroutineScope,
        feeders: List<Feeder> = emptyList(),
        state: FeederMonitorState = FeederMonitorState(),
        muted: Set<String> = emptySet(),
        healthEnabled: Boolean = false,
    ): FeederMonitor {
        feederRepo = mockk {
            every { isLoggedIn } returns loggedIn
            coEvery { refresh() } just Runs
            every { this@mockk.feeders } returns flowOf(feeders)
        }
        statsRepo = mockk()
        notifications = mockk(relaxed = true)
        monitorStateFlow = MutableStateFlow(state)
        mutedFlow = MutableStateFlow(muted)
        healthEnabledFlow = MutableStateFlow(healthEnabled)
        val settings = mockk<FeederSettings> {
            every { monitorState } returns fakeValue(monitorStateFlow)
            every { mutedFeeders } returns fakeValue(mutedFlow)
            every { deviceHealthWarningsEnabled } returns fakeValue(healthEnabledFlow)
            every { feederMonitorInterval } returns fakeValue(MutableStateFlow(Duration.ofMinutes(60)))
        }
        return FeederMonitor(
            feederRepo = feederRepo,
            feederStatsRepo = statsRepo,
            feederSettings = settings,
            notifications = notifications,
            appScope = scope,
        )
    }

    private fun feeder(id: String, status: FeederStatus) = Feeder(id = id, status = status)

    @Test
    fun `logged out cycle clears notifications and resets state`() = runTest {
        loggedIn.value = false
        val monitor = createMonitor(
            backgroundScope,
            state = FeederMonitorState(feeders = mapOf("a" to FeederTransitionState(outageActive = true))),
            muted = setOf("a"),
        )

        monitor.check()

        coVerify(atLeast = 1) { notifications.clearAll() }
        monitorStateFlow.value shouldBe FeederMonitorState()
        mutedFlow.value shouldBe emptySet()
        loggedIn.value = true
    }

    @Test
    fun `refresh failure aborts the cycle without notifications or state changes`() = runTest {
        val monitor = createMonitor(backgroundScope, feeders = listOf(feeder("a", FeederStatus.INACTIVE)))
        coEvery { feederRepo.refresh() } throws IOException("offline")

        monitor.check()

        coVerify(exactly = 0) { notifications.execute(any()) }
        monitorStateFlow.value shouldBe FeederMonitorState()
    }

    @Test
    fun `session lost during refresh aborts before evaluating`() = runTest {
        val monitor = createMonitor(backgroundScope, feeders = listOf(feeder("a", FeederStatus.INACTIVE)))
        coEvery { feederRepo.refresh() } answers { loggedIn.value = false }

        monitor.check()

        coVerify(exactly = 0) { notifications.execute(any()) }
        loggedIn.value = true
    }

    @Test
    fun `new outage posts one offline notification and persists state`() = runTest {
        val monitor = createMonitor(backgroundScope, feeders = listOf(feeder("a", FeederStatus.INACTIVE)))

        monitor.check()

        coVerify(exactly = 1) {
            notifications.execute(match { it is FeederTransitionEngine.Command.ShowOffline })
        }
        monitorStateFlow.value.feeders.getValue("a").outageActive shouldBe true
        monitorStateFlow.value.feeders.getValue("a").offlineNotificationPosted shouldBe true
    }

    @Test
    fun `mute racing the evaluation suppresses the notification and the posted flag`() = runTest {
        val monitor = createMonitor(backgroundScope, feeders = listOf(feeder("a", FeederStatus.INACTIVE)))
        // The mute lands after the first mute-set read: the first read returns empty, every
        // subsequent read sees the mute.
        var reads = 0
        val muteValue = mockk<DataStoreValue<Set<String>>> {
            every { flow } answers {
                reads++
                if (reads <= 1) flowOf(emptySet()) else flowOf(setOf("a"))
            }
            coEvery { update(any()) } returns DataStoreValue.Updated(emptySet(), setOf("a"))
        }
        val settings = mockk<FeederSettings> {
            every { monitorState } returns fakeValue(monitorStateFlow)
            every { mutedFeeders } returns muteValue
            every { deviceHealthWarningsEnabled } returns fakeValue(healthEnabledFlow)
        }
        val racedMonitor = FeederMonitor(
            feederRepo = feederRepo,
            feederStatsRepo = statsRepo,
            feederSettings = settings,
            notifications = notifications,
            appScope = backgroundScope,
        )

        racedMonitor.check()

        coVerify(exactly = 0) {
            notifications.execute(match { it is FeederTransitionEngine.Command.ShowOffline })
        }
        monitorStateFlow.value.feeders.getValue("a").offlineNotificationPosted shouldBe false
        // The post-execution sweep takes down anything a lost race may have shown.
        coVerify(atLeast = 1) { notifications.cancelAllFor("a") }
    }

    @Test
    fun `disabled health setting retires previous warnings`() = runTest {
        val monitor = createMonitor(
            backgroundScope,
            feeders = listOf(feeder("a", FeederStatus.ACTIVE)),
            state = FeederMonitorState(
                feeders = mapOf(
                    "a" to FeederTransitionState(
                        lastStatus = FeederStatus.ACTIVE,
                        healthWarnings = mapOf(
                            eu.darken.apl.feeder.core.stats.HealthMetric.CPU_TEMPERATURE to
                                eu.darken.apl.feeder.core.stats.HealthSeverity.WARN,
                        ),
                    ),
                ),
            ),
            healthEnabled = false,
        )

        monitor.check()

        coVerify(exactly = 1) {
            notifications.execute(FeederTransitionEngine.Command.CancelHealth("a"))
        }
        monitorStateFlow.value.feeders.getValue("a").healthWarnings shouldBe emptyMap()
    }
}
