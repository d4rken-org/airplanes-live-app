package eu.darken.apl.feeder.core.server

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import eu.darken.apl.common.serialization.SerializationModule
import eu.darken.apl.feeder.core.server.FeederServerState.Notified
import eu.darken.apl.feeder.core.server.FeederServerState.Refusal
import eu.darken.apl.feeder.core.server.FeederServerState.Upload
import eu.darken.apl.server.api.FeederMonitorEntry
import eu.darken.apl.server.api.FeederMonitorLatest
import eu.darken.apl.server.api.FeederMonitorLimits
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.io.File
import java.util.UUID

class FeederServerStoreTest : BaseTest() {

    private val json = SerializationModule().json()

    private lateinit var storeFile: File
    private lateinit var storeScope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: FeederServerStore

    private val key = stringPreferencesKey(FeederServerStore.KEY_STATE)

    @BeforeEach
    fun setup() {
        storeFile = File(IO_TEST_BASEDIR, "feeder-server-store-${UUID.randomUUID()}.preferences_pb").apply {
            parentFile?.mkdirs()
            delete()
        }
        storeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        dataStore = PreferenceDataStoreFactory.create(scope = storeScope, produceFile = { storeFile })
        store = FeederServerStore(dataStore, json)
    }

    @AfterEach
    fun teardown() {
        runBlocking { storeScope.coroutineContext.job.cancelAndJoin() }
        storeFile.delete()
    }

    private suspend fun raw(value: String) = dataStore.edit { it[key] = value }

    private val entry = FeederMonitorEntry(
        feederId = FEEDER_ID,
        monitorId = "monitor-1",
        offlineAfterMinutes = 360,
        status = "paused",
        pauseReason = "over_limit",
        createdAt = 1_759_000_000_000,
        updatedAt = 1_759_500_000_000,
        health = "unknown",
        unknownReason = "paused",
        healthSince = 1_759_600_000_000,
        problems = listOf("deaf"),
        insufficientEvidence = listOf("flapping"),
        lastSampleAt = 1_759_900_000_000,
        lastSuccessAt = 1_759_800_000_000,
        lastConnectedAt = 1_759_700_000_000,
        latest = FeederMonitorLatest(beastClients = 2, msgsPerSecond = 812.5, mlatPeers = 14),
        eventSeq = 31,
    )

    private val full = FeederServerState(
        installationId = "installation-a",
        listRevision = 7,
        cursor = 31,
        listState = "active",
        limits = FeederMonitorLimits(maxActiveFeeders = 3, maxStoredFeeders = 20),
        sampleIntervalSeconds = 300,
        lastListReadAt = 1_760_000_000_000,
        monitors = mapOf(FEEDER_ID to entry),
        uploads = mapOf(
            FEEDER_ID to Upload(syncedOfflineAfterMinutes = 360),
            OTHER_ID to Upload(refusal = Refusal.STORAGE_LIMIT),
        ),
        refusalContext = "context",
        uploadsNotBefore = 1_760_000_100_000,
        createsNotBefore = 1_760_003_600_000,
        readsNotBefore = 1_760_000_001_000,
        notified = mapOf(
            FEEDER_ID to Notified(monitorId = "monitor-1", offline = true, offlineSince = 1_759_600_000_000, problems = setOf("deaf")),
        ),
        notifiedInitialized = true,
    )

    @Test
    fun `a state survives encoding and decoding`() {
        json.decodeFromString<FeederServerState>(json.encodeToString(FeederServerState.serializer(), full)) shouldBe full
        json.decodeFromString<FeederServerState>(json.encodeToString(FeederServerState.serializer(), FeederServerState())) shouldBe
                FeederServerState()
    }

    @Test
    fun `a stored state is read back from its encoded value`() = runBlocking<Unit> {
        store.update { full }

        val stored = dataStore.data.first()[key]!!
        json.decodeFromString<FeederServerState>(stored) shouldBe full
        store.current() shouldBe full
    }

    @Test
    fun `an empty object decodes to the default state`() = runBlocking<Unit> {
        raw("{}")

        store.current() shouldBe FeederServerState()
    }

    @Test
    fun `a value without the newer fields keeps their defaults`() = runBlocking<Unit> {
        raw(
            """
            {"installationId":"installation-a","cursor":12,
             "monitors":{"$FEEDER_ID":{"feederId":"$FEEDER_ID","monitorId":"monitor-1","offlineAfterMinutes":2880,
               "status":"active","createdAt":1,"updatedAt":2,"health":"online","eventSeq":12}},
             "uploads":{"$FEEDER_ID":{}},
             "notified":{"$FEEDER_ID":{"monitorId":"monitor-1"}},
             "someFutureField":true}
            """.trimIndent()
        )

        with(store.current()) {
            installationId shouldBe "installation-a"
            cursor shouldBe 12
            listRevision shouldBe null
            limits shouldBe null
            monitors.getValue(FEEDER_ID).problems shouldBe emptyList()
            monitors.getValue(FEEDER_ID).latest shouldBe null
            uploads.getValue(FEEDER_ID) shouldBe Upload()
            notified.getValue(FEEDER_ID) shouldBe Notified(monitorId = "monitor-1")
            notifiedInitialized shouldBe false
            readsNotBefore shouldBe null
        }
    }

    @Test
    fun `a corrupt value falls back to the default state`() = runBlocking<Unit> {
        raw("{not json")
        store.current() shouldBe FeederServerState()

        raw("""{"monitors":5}""")
        store.current() shouldBe FeederServerState()
    }

    @Test
    fun `wiping returns to the default state`() = runBlocking<Unit> {
        store.update { full }

        store.wipe()

        store.current() shouldBe FeederServerState()
        dataStore.data.first()[key] shouldBe null
    }

    companion object {
        private const val FEEDER_ID = "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d"
        private const val OTHER_ID = "11111111-2222-4333-8444-555555555555"
    }
}
