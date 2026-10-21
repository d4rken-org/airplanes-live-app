package eu.darken.apl.main.core

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import eu.darken.apl.common.datastore.value
import eu.darken.apl.common.serialization.SerializationModule
import eu.darken.apl.common.units.AircraftUnits
import eu.darken.apl.common.units.DistanceUnitSetting
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

class GeneralSettingsUnitsTest : BaseTest() {

    private val json = SerializationModule().json()

    private lateinit var storeFile: File
    private val storeScopes = mutableListOf<CoroutineScope>()

    @BeforeEach
    fun setup() {
        storeFile = File(IO_TEST_BASEDIR, "general-settings-units-${storeCounter++}.preferences_pb")
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
    fun `defaults are aviation and automatic`() = runBlocking<Unit> {
        val settings = GeneralSettings(openStore(), json)

        settings.aircraftUnits.value() shouldBe AircraftUnits.AVIATION
        settings.distanceUnit.value() shouldBe DistanceUnitSetting.AUTOMATIC
    }

    @Test
    fun `stored values keep their names`() = runBlocking<Unit> {
        val store = openStore()
        val settings = GeneralSettings(store, json)

        settings.aircraftUnits.value(AircraftUnits.METRIC)
        settings.distanceUnit.value(DistanceUnitSetting.NAUTICAL_MILES)

        store.data.first().apply {
            this[stringPreferencesKey("core.units.aircraft")] shouldBe "\"METRIC\""
            this[stringPreferencesKey("core.units.distance")] shouldBe "\"NAUTICAL_MILES\""
        }
    }

    @Test
    fun `stored names read back`() = runBlocking<Unit> {
        val store = openStore()
        store.edit {
            it[stringPreferencesKey("core.units.aircraft")] = "\"IMPERIAL\""
            it[stringPreferencesKey("core.units.distance")] = "\"MILES\""
        }
        val settings = GeneralSettings(store, json)

        settings.aircraftUnits.value() shouldBe AircraftUnits.IMPERIAL
        settings.distanceUnit.value() shouldBe DistanceUnitSetting.MILES
    }

    @Test
    fun `unknown stored values fall back to the defaults`() = runBlocking<Unit> {
        val store = openStore()
        store.edit {
            it[stringPreferencesKey("core.units.aircraft")] = "\"FURLONGS\""
            it[stringPreferencesKey("core.units.distance")] = "\"LEAGUES\""
        }
        val settings = GeneralSettings(store, json)

        settings.aircraftUnits.value() shouldBe AircraftUnits.AVIATION
        settings.distanceUnit.value() shouldBe DistanceUnitSetting.AUTOMATIC
    }

    companion object {
        private var storeCounter = 0
    }
}
