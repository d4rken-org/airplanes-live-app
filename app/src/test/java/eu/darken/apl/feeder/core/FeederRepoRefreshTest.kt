package eu.darken.apl.feeder.core

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import eu.darken.apl.common.datastore.createValue
import eu.darken.apl.common.datastore.value
import eu.darken.apl.common.serialization.SerializationModule
import eu.darken.apl.feeder.core.api.FeedInfos
import eu.darken.apl.feeder.core.api.FeederEndpoint
import eu.darken.apl.feeder.core.config.FeederConfig
import eu.darken.apl.feeder.core.config.FeederGroup
import eu.darken.apl.feeder.core.config.FeederSettings
import eu.darken.apl.feeder.core.server.FeederMonitorRunner
import eu.darken.apl.feeder.core.server.FeederMonitorSync
import eu.darken.apl.feeder.core.server.FeederMonitorSyncScheduler
import eu.darken.apl.feeder.core.server.FeederServerMode
import eu.darken.apl.feeder.core.server.FeederServerState
import eu.darken.apl.feeder.core.server.FeederServerView
import eu.darken.apl.server.api.FeederMonitorEntry
import io.kotest.assertions.throwables.shouldThrow
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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

class FeederRepoRefreshTest : BaseTest() {

    private val json = SerializationModule().json()
    private lateinit var storeFile: File
    private lateinit var storeScope: CoroutineScope
    private lateinit var settings: FeederSettings
    private val endpoint = mockk<FeederEndpoint>()
    private val modeState = MutableStateFlow(FeederServerMode.State(access = null, isActive = false))
    private val serverMode = mockk<FeederServerMode>().apply {
        every { state } returns modeState
        coEvery { current() } answers { modeState.value }
    }
    private val runner = mockk<FeederMonitorRunner>()
    private val scheduler = mockk<FeederMonitorSyncScheduler>(relaxed = true)

    @BeforeEach
    fun setup() {
        storeFile = File(IO_TEST_BASEDIR, "feeder-repo-refresh-${storeCounter++}.preferences_pb")
        storeFile.parentFile?.mkdirs()
        storeFile.delete()
        storeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val store: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = storeScope,
            produceFile = { storeFile },
        )

        val group = store.createValue("feeder.group", FeederGroup(), json)
        settings = mockk {
            every { feederGroup } returns group
        }
    }

    @AfterEach
    fun teardown() {
        runBlocking { storeScope.coroutineContext.job.cancelAndJoin() }
        storeFile.delete()
    }

    private fun repo(scope: CoroutineScope) = FeederRepo(
        appScope = scope,
        feederSettings = settings,
        feederEndpoint = endpoint,
        serverMode = serverMode,
        monitorRunner = runner,
        syncScheduler = scheduler,
    )

    private fun serverMode(state: FeederServerState = FeederServerState(installationId = "inst")) {
        modeState.value = FeederServerMode.State(access = null, isActive = true, server = state)
    }

    private fun entry(
        feederId: String,
        status: String = "active",
        health: String = "online",
    ) = FeederMonitorEntry(
        feederId = feederId,
        monitorId = "m-$feederId",
        offlineAfterMinutes = 60,
        status = status,
        createdAt = 0,
        updatedAt = 0,
        health = health,
        eventSeq = 0,
    )

    private fun mlatInfos(user: String) = FeedInfos(
        beast = listOf(
            FeedInfos.Beast(
                uuid = UUID.randomUUID(),
                avgKBitsPerSecond = 1.0,
                connTime = 10,
                messageRate = 5.0,
                positionRate = 0.0,
                recentRtt = 0,
                positions = 0,
            )
        ),
        mlat = listOf(
            FeedInfos.Mlat(
                user = user,
                uuid = null,
                messageRate = 1.0,
                peerCount = 3,
                badSyncTimeout = 0,
                outlierPercent = 0f,
                syncInterest = emptyList(),
                mlatInterest = emptyList(),
            )
        ),
    )

    private suspend fun monitor(vararg configs: FeederConfig) {
        settings.feederGroup.value(FeederGroup(configs = configs.toSet()))
    }

    @Test
    fun `refresh without server monitoring asks airplanes live nothing`() = runBlocking<Unit> {
        monitor(FeederConfig.newFeeder(FEEDER_ID))
        val repo = repo(this)

        repo.refresh()

        coVerify(exactly = 0) { endpoint.getFeedInfos(any()) }
        coVerify(exactly = 0) { runner.run(any(), any()) }
        repo.isRefreshing.value shouldBe false
    }

    @Test
    fun `without server monitoring feeders carry no server data`() = runBlocking<Unit> {
        monitor(FeederConfig.newFeeder(FEEDER_ID))

        repo(this).feeders.first().single().server shouldBe null
    }

    @Test
    fun `refresh in server mode runs the server sync instead of the feed lookup`() = runBlocking<Unit> {
        monitor(FeederConfig.newFeeder(FEEDER_ID))
        serverMode()
        val done = FeederMonitorSync.Result.Done(moreWork = true)
        coEvery { runner.run(any(), any()) } returns done
        val repo = repo(this)

        repo.refresh()

        coVerify(exactly = 1) { runner.run("feeder-refresh", true) }
        verify(exactly = 1) { scheduler.afterRun(done) }
        coVerify(exactly = 0) { endpoint.getFeedInfos(any()) }
        repo.isRefreshing.value shouldBe false
    }

    @Test
    fun `a failing server sync reaches the caller`() = runBlocking<Unit> {
        monitor(FeederConfig.newFeeder(FEEDER_ID))
        serverMode()
        coEvery { runner.run(any(), any()) } throws IOException("down")
        val repo = repo(this)

        shouldThrow<IOException> { repo.refresh() }

        repo.isRefreshing.value shouldBe false
    }

    private suspend fun fillsOnlyEmptyLabels() = coroutineScope {
        monitor(FeederConfig.newFeeder(FEEDER_ID), FeederConfig.newFeeder(OTHER_ID).copy(label = "Mine"))
        coEvery { endpoint.getFeedInfos(any()) } returns mapOf(
            FEEDER_ID to Result.success(mlatInfos("Rooftop")),
            OTHER_ID to Result.success(mlatInfos("Garden")),
        )

        coroutineScope { repo(this).fillLabelsDetached(listOf(FEEDER_ID, OTHER_ID)) }

        val labels = settings.feederGroup.value().configs.associate { it.receiverId to it.label }
        labels shouldBe mapOf(FEEDER_ID to "Rooftop", OTHER_ID to "Mine")
    }

    @Test
    fun `a detached label fill in server mode fills only empty labels`() = runBlocking<Unit> {
        serverMode()

        fillsOnlyEmptyLabels()
    }

    @Test
    fun `a detached label fill without server monitoring fills only empty labels`() = runBlocking<Unit> {
        fillsOnlyEmptyLabels()
    }

    @Test
    fun `a failing label lookup changes nothing and escapes nowhere`() = runBlocking<Unit> {
        monitor(FeederConfig.newFeeder(FEEDER_ID))
        coEvery { endpoint.getFeedInfos(any()) } throws IOException("down")

        coroutineScope { repo(this).fillLabelsDetached(listOf(FEEDER_ID)) }

        settings.feederGroup.value().configs.single().label shouldBe null
    }

    @Test
    fun `server mode feeders carry the entry of their canonical id`() = runBlocking<Unit> {
        monitor(FeederConfig.newFeeder(FEEDER_ID.uppercase()), FeederConfig.newFeeder("not-a-uuid"))
        serverMode(FeederServerState(installationId = "inst", monitors = mapOf(FEEDER_ID to entry(FEEDER_ID))))

        val servers = repo(this).feeders.first().associate { it.id to it.server }

        servers[FEEDER_ID.uppercase()] shouldBe FeederServerView(FEEDER_ID, entry(FEEDER_ID))
        servers["not-a-uuid"] shouldBe FeederServerView(feederId = null)
    }

    @Test
    fun `server mode offline needs the server's offline verdict and the alert switch and no snooze`() = runBlocking<Unit> {
        val repo = repo(this)
        val alerting = FeederConfig.newFeeder(FEEDER_ID).copy(offlineCheckTimeout = Duration.ofHours(1))
        fun feeder(config: FeederConfig, entry: FeederMonitorEntry?) =
            Feeder(config = config, server = FeederServerView(FEEDER_ID, entry))

        repo.isOffline(feeder(alerting, entry(FEEDER_ID, health = "offline"))) shouldBe true
        repo.isOffline(feeder(alerting, entry(FEEDER_ID, health = "online"))) shouldBe false
        repo.isOffline(feeder(alerting, entry(FEEDER_ID, status = "paused", health = "offline"))) shouldBe false
        repo.isOffline(feeder(alerting, null)) shouldBe false
        repo.isOffline(feeder(alerting.copy(offlineCheckTimeout = null), entry(FEEDER_ID, health = "offline"))) shouldBe false
        repo.isOffline(
            feeder(alerting.copy(offlineCheckSnoozedAt = Instant.EPOCH), entry(FEEDER_ID, health = "offline"))
        ) shouldBe false
    }

    @Test
    fun `a feeder without server data is never offline`() = runBlocking<Unit> {
        val alerting = FeederConfig.newFeeder(FEEDER_ID).copy(offlineCheckTimeout = Duration.ofHours(1))

        repo(this).isOffline(Feeder(config = alerting)) shouldBe false
    }

    companion object {
        private var storeCounter = 0
        private const val FEEDER_ID = "0a1b2c3d-0000-4000-8000-000000000001"
        private const val OTHER_ID = "0a1b2c3d-0000-4000-8000-000000000002"
    }
}
