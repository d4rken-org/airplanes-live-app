package eu.darken.apl.map.core

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import eu.darken.apl.common.datastore.value
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

class MapLabelSettingsTest : BaseTest() {

    private val json = SerializationModule().json()

    private lateinit var storeFile: File
    private val storeScopes = mutableListOf<CoroutineScope>()

    @BeforeEach
    fun setup() {
        storeFile = File(IO_TEST_BASEDIR, "map-label-settings-${storeCounter++}.preferences_pb")
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

    @Test
    fun `defaults are callsign only`() = runBlocking<Unit> {
        val settings = MapSettings(openStore(), json)

        settings.labelFields.first() shouldBe MapLabelFields(callsign = true)
    }

    @Test
    fun `enabling type then disabling callsign leaves type only`() = runBlocking<Unit> {
        val settings = MapSettings(openStore(), json)

        settings.setLabelField(MapLabelField.TYPE, true) shouldBe true
        settings.setLabelField(MapLabelField.CALLSIGN, false) shouldBe true

        settings.labelFields.first() shouldBe MapLabelFields(callsign = false, type = true)
    }

    @Test
    fun `disabling two enabled fields in a row keeps one`() = runBlocking<Unit> {
        val settings = MapSettings(openStore(), json)
        settings.setLabelField(MapLabelField.SPEED, true)

        settings.setLabelField(MapLabelField.CALLSIGN, false) shouldBe true
        settings.setLabelField(MapLabelField.SPEED, false) shouldBe false

        settings.labelFields.first() shouldBe MapLabelFields(callsign = false, speed = true)
    }

    @Test
    fun `disabling the only enabled field is refused`() = runBlocking<Unit> {
        val store = openStore()
        val settings = MapSettings(store, json)

        settings.setLabelField(MapLabelField.CALLSIGN, false) shouldBe false

        settings.labelFields.first() shouldBe MapLabelFields()
        store.data.first()[booleanPreferencesKey("map.native.labels.callsign")] shouldBe null
    }

    @Test
    fun `a stored state without any field reads as the defaults`() = runBlocking<Unit> {
        val store = openStore()
        store.edit {
            it[booleanPreferencesKey("map.native.labels.callsign")] = false
            it[booleanPreferencesKey("map.native.labels.type")] = false
            it[booleanPreferencesKey("map.native.labels.altitude")] = false
            it[booleanPreferencesKey("map.native.labels.speed")] = false
        }
        val settings = MapSettings(store, json)

        settings.labelFields.first() shouldBe MapLabelFields()
    }

    @Test
    fun `stored fields use their keys`() = runBlocking<Unit> {
        val store = openStore()
        val settings = MapSettings(store, json)

        settings.setLabelField(MapLabelField.ALTITUDE, true)

        store.data.first().apply {
            this[booleanPreferencesKey("map.native.labels.callsign")] shouldBe true
            this[booleanPreferencesKey("map.native.labels.type")] shouldBe false
            this[booleanPreferencesKey("map.native.labels.altitude")] shouldBe true
            this[booleanPreferencesKey("map.native.labels.speed")] shouldBe false
        }
    }

    @Test
    fun `the labels switch is unaffected`() = runBlocking<Unit> {
        val store = openStore()
        store.edit { it[booleanPreferencesKey("map.native.labels.enabled")] = false }
        val settings = MapSettings(store, json)

        settings.setLabelField(MapLabelField.TYPE, true)
        settings.setLabelField(MapLabelField.CALLSIGN, false)

        settings.isLabelsEnabled.value() shouldBe false
        store.data.first()[booleanPreferencesKey("map.native.labels.enabled")] shouldBe false
    }

    companion object {
        private var storeCounter = 0
    }
}
