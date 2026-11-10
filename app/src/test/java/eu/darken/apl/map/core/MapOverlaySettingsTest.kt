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

class MapOverlaySettingsTest : BaseTest() {

    private val json = SerializationModule().json()

    private lateinit var storeFile: File
    private val storeScopes = mutableListOf<CoroutineScope>()

    @BeforeEach
    fun setup() {
        storeFile = File(IO_TEST_BASEDIR, "map-overlay-settings-${storeCounter++}.preferences_pb")
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
    fun `no overlay is on by default`() = runBlocking<Unit> {
        val settings = MapSettings(openStore(), json)

        settings.enabledOverlays.flow.first() shouldBe emptySet()
    }

    @Test
    fun `switching overlays on and off`() = runBlocking<Unit> {
        val settings = MapSettings(openStore(), json)

        settings.setOverlayEnabled("radar", true)
        settings.setOverlayEnabled("military", true)
        settings.enabledOverlays.flow.first() shouldBe setOf("radar", "military")

        settings.setOverlayEnabled("radar", false)
        settings.enabledOverlays.flow.first() shouldBe setOf("military")

        settings.setOverlayEnabled("military", false)
        settings.enabledOverlays.flow.first() shouldBe emptySet()
    }

    @Test
    fun `switching on twice keeps one entry`() = runBlocking<Unit> {
        val settings = MapSettings(openStore(), json)

        settings.setOverlayEnabled("radar", true)
        settings.setOverlayEnabled("radar", true)

        settings.enabledOverlays.flow.first() shouldBe setOf("radar")
    }

    @Test
    fun `the choice survives reopening the store`() = runBlocking<Unit> {
        MapSettings(openStore(), json).apply {
            setOverlayEnabled("radar", true)
            setOverlayEnabled("restrictions", true)
        }
        closeStores()

        val reopened = MapSettings(openStore(), json)

        reopened.enabledOverlays.flow.first() shouldBe setOf("radar", "restrictions")
    }

    @Test
    fun `an id the app doesn't know is kept`() = runBlocking<Unit> {
        val store = openStore()
        store.edit { it[stringSetPreferencesKey("map.native.overlays.enabled")] = setOf("radar", "lightning") }
        val settings = MapSettings(store, json)

        settings.setOverlayEnabled("military", true)

        settings.enabledOverlays.flow.first() shouldBe setOf("radar", "lightning", "military")
    }

    companion object {
        private var storeCounter = 0
    }
}
