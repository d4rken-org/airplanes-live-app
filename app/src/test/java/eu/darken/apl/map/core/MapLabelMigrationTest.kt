package eu.darken.apl.map.core

import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import eu.darken.apl.common.serialization.SerializationModule
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

class MapLabelMigrationTest : BaseTest() {

    private val json = SerializationModule().json()

    private lateinit var storeFile: File
    private val storeScopes = mutableListOf<CoroutineScope>()

    private val keyMarker = booleanPreferencesKey("map.native.labels.migrated")
    private val keyLabels = booleanPreferencesKey("map.native.labels.enabled")
    private val keyCallsign = booleanPreferencesKey("map.native.labels.callsign")
    private val keyType = booleanPreferencesKey("map.native.labels.type")
    private val keyAltitude = booleanPreferencesKey("map.native.labels.altitude")
    private val keySpeed = booleanPreferencesKey("map.native.labels.speed")
    private val keyCamera = stringPreferencesKey("map.last.camera")
    private val keyStyle = stringPreferencesKey("map.native.style")
    private val keyKinds = stringSetPreferencesKey("map.native.kinds")
    private val keyOverlays = stringSetPreferencesKey("map.native.overlays.enabled")

    private val cameraJson = """{"latitude":50.0,"longitude":8.0,"zoom":7.0}"""

    @BeforeEach
    fun setup() {
        storeFile = File(IO_TEST_BASEDIR, "map-label-migration-${storeCounter++}.preferences_pb")
        storeFile.parentFile?.mkdirs()
        storeFile.delete()
    }

    @AfterEach
    fun teardown() {
        runBlocking { closeStores() }
        storeFile.delete()
    }

    private suspend fun closeStores() {
        storeScopes.forEach { it.coroutineContext.job.cancelAndJoin() }
        storeScopes.clear()
    }

    private fun openStore(migrations: List<DataMigration<Preferences>>): DataStore<Preferences> {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also { storeScopes.add(it) }
        return PreferenceDataStoreFactory.create(scope = scope, migrations = migrations, produceFile = { storeFile })
    }

    /** Writes the legacy file without the migration, then reopens it the way the app does. */
    private suspend fun migrated(seed: (MutablePreferences) -> Unit): DataStore<Preferences> {
        openStore(emptyList()).edit { seed(it) }
        closeStores()
        return openStore(listOf(MapLabelMigration()))
    }

    @Test
    fun `fresh install opening the map after onboarding gets no labels`() = runBlocking<Unit> {
        val store = migrated { }

        store.data.first().asMap() shouldBe mapOf<Preferences.Key<*>, Any>(Pair(keyMarker, true))
        MapSettings(store, json).labelFields.first() shouldBe MapLabelFields(callsign = false, type = false, altitude = false, speed = false)
    }

    @Test
    fun `existing install without the labels switch stored keeps callsign labels`() = runBlocking<Unit> {
        val store = migrated { it[keyCamera] = cameraJson }

        store.data.first().apply {
            this[keyCallsign] shouldBe true
            this[keyType] shouldBe false
            this[keyAltitude] shouldBe false
            this[keySpeed] shouldBe false
            this[keyLabels] shouldBe null
            this[keyMarker] shouldBe true
        }
        MapSettings(store, json).labelFields.first() shouldBe MapLabelFields(callsign = true)
    }

    @Test
    fun `existing install with labels stored on keeps callsign labels`() = runBlocking<Unit> {
        val store = migrated { it[keyLabels] = true }

        store.data.first().apply {
            this[keyCallsign] shouldBe true
            this[keyType] shouldBe false
            this[keyAltitude] shouldBe false
            this[keySpeed] shouldBe false
            this[keyLabels] shouldBe null
            this[keyMarker] shouldBe true
        }
    }

    @Test
    fun `existing install with labels off turns all fields off`() = runBlocking<Unit> {
        val store = migrated { it[keyLabels] = false }

        store.data.first().apply {
            this[keyCallsign] shouldBe false
            this[keyType] shouldBe false
            this[keyAltitude] shouldBe false
            this[keySpeed] shouldBe false
            this[keyLabels] shouldBe null
            this[keyMarker] shouldBe true
        }
    }

    @Test
    fun `existing install with labels off and chosen fields turns all fields off`() = runBlocking<Unit> {
        val store = migrated {
            it[keyLabels] = false
            it[keyCallsign] = true
            it[keyType] = true
            it[keyAltitude] = false
            it[keySpeed] = false
        }

        store.data.first().apply {
            this[keyCallsign] shouldBe false
            this[keyType] shouldBe false
            this[keyAltitude] shouldBe false
            this[keySpeed] shouldBe false
            this[keyLabels] shouldBe null
            this[keyMarker] shouldBe true
        }
    }

    @Test
    fun `existing install with labels on keeps chosen fields`() = runBlocking<Unit> {
        val store = migrated {
            it[keyCallsign] = false
            it[keyType] = true
            it[keyAltitude] = false
            it[keySpeed] = false
        }

        store.data.first().apply {
            this[keyCallsign] shouldBe false
            this[keyType] shouldBe true
            this[keyAltitude] shouldBe false
            this[keySpeed] shouldBe false
            this[keyLabels] shouldBe null
            this[keyMarker] shouldBe true
        }
    }

    @Test
    fun `other map settings survive the migration`() = runBlocking<Unit> {
        val store = migrated {
            it[keyCamera] = cameraJson
            it[keyStyle] = "dark"
            it[keyKinds] = setOf("heavy", "helicopter")
            it[keyOverlays] = setOf("radar")
            it[keyLabels] = false
        }

        store.data.first().apply {
            this[keyCamera] shouldBe cameraJson
            this[keyStyle] shouldBe "dark"
            this[keyKinds] shouldBe setOf("heavy", "helicopter")
            this[keyOverlays] shouldBe setOf("radar")
            this[keyCallsign] shouldBe false
            this[keyMarker] shouldBe true
        }
    }

    @Test
    fun `the migration runs once`() = runBlocking<Unit> {
        val first = migrated { it[keyCamera] = cameraJson }
        val settings = MapSettings(first, json)
        settings.labelFields.first() shouldBe MapLabelFields(callsign = true)

        settings.setLabelField(MapLabelField.CALLSIGN, false) shouldBe true
        closeStores()

        val reopened = openStore(listOf(MapLabelMigration()))
        MapSettings(reopened, json).labelFields.first() shouldBe MapLabelFields(callsign = false, type = false, altitude = false, speed = false)
        reopened.data.first()[keyMarker] shouldBe true
    }

    companion object {
        private var storeCounter = 0
    }
}
