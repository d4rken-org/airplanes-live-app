package eu.darken.apl.map.core

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
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

class MapKindSettingsTest : BaseTest() {

    private val json = SerializationModule().json()

    private lateinit var storeFile: File
    private val storeScopes = mutableListOf<CoroutineScope>()

    @BeforeEach
    fun setup() {
        storeFile = File(IO_TEST_BASEDIR, "map-kind-settings-${storeCounter++}.preferences_pb")
        storeFile.parentFile?.mkdirs()
        storeFile.delete()
    }

    @AfterEach
    fun teardown() {
        runBlocking { storeScopes.forEach { it.coroutineContext.job.cancelAndJoin() } }
        storeScopes.clear()
        storeFile.delete()
    }

    private fun openStore(): DataStore<Preferences> {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also { storeScopes.add(it) }
        return PreferenceDataStoreFactory.create(scope = scope, produceFile = { storeFile })
    }

    private suspend fun closeStores() {
        storeScopes.forEach { it.coroutineContext.job.cancelAndJoin() }
        storeScopes.clear()
    }

    @Test
    fun `all kinds are shown by default`() = runBlocking<Unit> {
        val settings = MapSettings(openStore(), json)

        settings.aircraftKinds.flow.first() shouldBe emptySet()
    }

    @Test
    fun `toggling a kind while all are shown shows only that kind`() = runBlocking<Unit> {
        val settings = MapSettings(openStore(), json)

        settings.toggleAircraftKind(AircraftKind.HELICOPTER)

        settings.aircraftKinds.flow.first() shouldBe setOf(AircraftKind.HELICOPTER)
    }

    @Test
    fun `toggling a second kind adds it`() = runBlocking<Unit> {
        val settings = MapSettings(openStore(), json)

        settings.toggleAircraftKind(AircraftKind.HELICOPTER)
        settings.toggleAircraftKind(AircraftKind.GLIDER)

        settings.aircraftKinds.flow.first() shouldBe setOf(AircraftKind.HELICOPTER, AircraftKind.GLIDER)
    }

    @Test
    fun `toggling a chosen kind removes it`() = runBlocking<Unit> {
        val settings = MapSettings(openStore(), json)

        settings.toggleAircraftKind(AircraftKind.HELICOPTER)
        settings.toggleAircraftKind(AircraftKind.GLIDER)
        settings.toggleAircraftKind(AircraftKind.HELICOPTER)

        settings.aircraftKinds.flow.first() shouldBe setOf(AircraftKind.GLIDER)
    }

    @Test
    fun `removing the last chosen kind shows all again`() = runBlocking<Unit> {
        val settings = MapSettings(openStore(), json)

        settings.toggleAircraftKind(AircraftKind.HELICOPTER)
        settings.toggleAircraftKind(AircraftKind.HELICOPTER)

        settings.aircraftKinds.flow.first() shouldBe emptySet()
    }

    @Test
    fun `choosing every kind reads as all`() = runBlocking<Unit> {
        val settings = MapSettings(openStore(), json)

        AircraftKind.entries.forEach { settings.toggleAircraftKind(it) }

        settings.aircraftKinds.flow.first() shouldBe emptySet()
    }

    @Test
    fun `show all clears the choice`() = runBlocking<Unit> {
        val settings = MapSettings(openStore(), json)
        settings.toggleAircraftKind(AircraftKind.DRONE)
        settings.toggleAircraftKind(AircraftKind.BALLOON)

        settings.showAllAircraftKinds()

        settings.aircraftKinds.flow.first() shouldBe emptySet()
    }

    @Test
    fun `the choice survives reopening the store`() = runBlocking<Unit> {
        MapSettings(openStore(), json).apply {
            toggleAircraftKind(AircraftKind.GROUND)
            toggleAircraftKind(AircraftKind.UNKNOWN)
        }
        closeStores()

        val reopened = MapSettings(openStore(), json)

        reopened.aircraftKinds.flow.first() shouldBe setOf(AircraftKind.GROUND, AircraftKind.UNKNOWN)
    }

    @Test
    fun `an unknown stored kind is dropped`() = runBlocking<Unit> {
        val store = openStore()
        store.edit { it[stringSetPreferencesKey("map.native.kinds")] = setOf("helicopter", "zeppelin") }
        val settings = MapSettings(store, json)

        settings.aircraftKinds.flow.first() shouldBe setOf(AircraftKind.HELICOPTER)
    }

    companion object {
        private var storeCounter = 0
    }
}
