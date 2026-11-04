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
import eu.darken.apl.feeder.core.stats.BeastStatsDao
import eu.darken.apl.feeder.core.stats.BeastStatsEntity
import eu.darken.apl.feeder.core.stats.FeederStatsDatabase
import eu.darken.apl.feeder.core.stats.MlatStatsDao
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.HttpException
import retrofit2.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import testhelper.BaseTest
import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.UUID

class FeederRepoRefreshTest : BaseTest() {

    private val json = SerializationModule().json()
    private lateinit var storeFile: File
    private lateinit var storeScope: CoroutineScope
    private lateinit var settings: FeederSettings
    private val beastDao = mockk<BeastStatsDao>(relaxed = true)
    private val mlatDao = mockk<MlatStatsDao>(relaxed = true)
    private val database = mockk<FeederStatsDatabase>()
    private val endpoint = mockk<FeederEndpoint>()

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
        val observations = store.createValue(
            "feeder.observations",
            emptyMap<ReceiverId, FeederObservation>(),
            json,
            onErrorFallbackToDefault = true,
        )
        val lastCleanup = store.createValue("feeder.cleanup.last", Instant.EPOCH, json)
        settings = mockk {
            every { feederGroup } returns group
            every { this@mockk.observations } returns observations
            every { this@mockk.lastCleanup } returns lastCleanup
        }

        every { database.beastStats } returns beastDao
        every { database.mlatStats } returns mlatDao
        every { beastDao.firehose() } returns flowOf(null)
        every { mlatDao.firehose() } returns flowOf(null)
        every { beastDao.getLatest(any()) } returns flowOf(null)
        every { mlatDao.getLatest(any()) } returns flowOf(null)
        coEvery { beastDao.getLatestFeeding(any()) } returns null
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
        feederStatsDatabase = database,
        networkStateProvider = mockk(),
    )

    private suspend fun monitor(vararg configs: FeederConfig) {
        settings.feederGroup.value(FeederGroup(configs = configs.toSet()))
    }

    private fun beast(rate: Double) = FeedInfos.Beast(
        uuid = UUID.randomUUID(),
        avgKBitsPerSecond = 1.0,
        connTime = 10,
        messageRate = rate,
        positionRate = 0.0,
        recentRtt = 0,
        positions = 0,
    )

    private fun infos(vararg rates: Double) = FeedInfos(beast = rates.map { beast(it) }, mlat = emptyList())

    private fun http(code: Int) = HttpException(Response.error<Any>(code, "".toResponseBody(null)))

    @Test
    fun `one failing feeder doesn't keep the others from being stored`() = runBlocking<Unit> {
        monitor(FeederConfig.newFeeder("good"), FeederConfig.newFeeder("typo"))
        coEvery { endpoint.getFeedInfos(any()) } returns mapOf(
            "good" to Result.success(infos(5.0)),
            "typo" to Result.failure(http(400)),
        )

        val error = shouldThrow<FeederRefreshFailure> { repo(this).refresh() }

        error.failed.keys shouldBe setOf("typo")
        error.isPartial shouldBe true
        coVerify(exactly = 1) { beastDao.insert(match { it.receiverId == "good" }) }
        val observations = settings.observations.value()
        observations["good"]!!.status shouldBe FeederObservation.Status.FEEDING
        observations["typo"]!!.isCheckFailing shouldBe true
    }

    @Test
    fun `all feeders failing is not partial`() = runBlocking<Unit> {
        monitor(FeederConfig.newFeeder("a"))
        coEvery { endpoint.getFeedInfos(any()) } returns mapOf("a" to Result.failure(IOException("down")))

        shouldThrow<FeederRefreshFailure> { repo(this).refresh() }.isPartial shouldBe false
    }

    @Test
    fun `an empty answer is recorded although it stores no stats`() = runBlocking<Unit> {
        monitor(FeederConfig.newFeeder("a"))
        coEvery { endpoint.getFeedInfos(any()) } returns mapOf("a" to Result.success(infos()))

        repo(this).refresh()

        settings.observations.value()["a"]!!.status shouldBe FeederObservation.Status.NOT_SEEN
        coVerify(exactly = 0) { beastDao.insert(any()) }
    }

    @Test
    fun `a feeder without an observation starts from its newest row with messages`() = runBlocking<Unit> {
        val fedAt = Instant.parse("2026-10-30T12:00:00Z")
        monitor(FeederConfig.newFeeder("a"))
        coEvery { beastDao.getLatestFeeding("a") } returns BeastStatsEntity(
            receiverId = "a",
            receivedAt = fedAt,
            positionRate = 0.0,
            positions = 0,
            messageRate = 5.0,
            bandwidth = 0.0,
            connectionTime = 0,
            latency = 0,
        )
        coEvery { endpoint.getFeedInfos(any()) } returns mapOf("a" to Result.success(infos()))

        repo(this).refresh()

        settings.observations.value()["a"]!!.lastFeedingAt shouldBe fedAt
        // The seed row may be old enough for the retention cleanup, which must not run first
        coVerifyOrder {
            beastDao.getLatestFeeding("a")
            beastDao.deleteBefore(any())
        }
    }

    @Test
    fun `snooze clears only when the feeder carries messages again`() = runBlocking<Unit> {
        val snoozedAt = Instant.parse("2026-11-01T00:00:00Z")
        monitor(FeederConfig.newFeeder("a").copy(offlineCheckSnoozedAt = snoozedAt))

        coEvery { endpoint.getFeedInfos(any()) } returns mapOf("a" to Result.success(infos(0.0)))
        repo(this).refresh()
        settings.feederGroup.value().configs.single().offlineCheckSnoozedAt shouldBe snoozedAt

        coEvery { endpoint.getFeedInfos(any()) } returns mapOf("a" to Result.success(infos(3.0)))
        repo(this).refresh()
        settings.feederGroup.value().configs.single().offlineCheckSnoozedAt shouldBe null
    }

    @Test
    /** Removes "a" while the fetch for "a" and "b" is in flight, then lets the fetch answer [resultForA]. */
    private suspend fun CoroutineScope.removeDuringFetch(resultForA: Result<FeedInfos>) {
        monitor(FeederConfig.newFeeder("a"), FeederConfig.newFeeder("b"))
        settings.observations.value(mapOf("a" to FeederObservation(lastFeedingAt = Instant.EPOCH)))
        val fetchStarted = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        coEvery { endpoint.getFeedInfos(any()) } coAnswers {
            fetchStarted.complete(Unit)
            release.await()
            mapOf("a" to resultForA, "b" to Result.success(infos(5.0)))
        }
        val repo = repo(this)

        val refresh = async { repo.refresh() }
        fetchStarted.await()
        repo.removeFeeder("a")
        settings.observations.value() shouldNotContainKey "a"
        release.complete(Unit)
        refresh.await()

        settings.observations.value() shouldNotContainKey "a"
        settings.observations.value()["b"] shouldNotBe null
    }

    @Test
    fun `a feeder removed while its fetch runs stays removed`() = runBlocking<Unit> {
        removeDuringFetch(Result.success(infos(5.0)))

        coVerify(exactly = 0) { beastDao.insert(match { it.receiverId == "a" }) }
    }

    @Test
    fun `a removed feeder's failed fetch is no failure of the refresh`() = runBlocking<Unit> {
        // refresh.await() inside would throw if the failure still counted
        removeDuringFetch(Result.failure(IOException("gone")))
    }

    companion object {
        private var storeCounter = 0
    }
}
