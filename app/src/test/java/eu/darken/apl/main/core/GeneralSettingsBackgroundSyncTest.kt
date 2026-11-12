package eu.darken.apl.main.core

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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

class GeneralSettingsBackgroundSyncTest : BaseTest() {

    private val json = SerializationModule().json()
    private val key = stringPreferencesKey("core.sync.background.level")

    private lateinit var storeFile: File
    private val storeScopes = mutableListOf<CoroutineScope>()

    @BeforeEach
    fun setup() {
        storeFile = File(IO_TEST_BASEDIR, "general-settings-background-sync-${storeCounter++}.preferences_pb")
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
    fun `an empty store reads as never chosen`() = runBlocking<Unit> {
        GeneralSettings(openStore(), json).backgroundSyncLevel.value() shouldBe null
    }

    @Test
    fun `each level is stored under its name and reads back`() = runBlocking<Unit> {
        val store = openStore()
        val settings = GeneralSettings(store, json)

        mapOf(
            BackgroundSyncLevel.FAST to "\"FAST\"",
            BackgroundSyncLevel.BALANCED to "\"BALANCED\"",
            BackgroundSyncLevel.SLOW to "\"SLOW\"",
        ).forEach { (level, stored) ->
            settings.backgroundSyncLevel.value(level)

            store.data.first()[key] shouldBe stored
            GeneralSettings(store, json).backgroundSyncLevel.value() shouldBe level
        }
    }

    @Test
    fun `an unknown name reads as never chosen`() = runBlocking<Unit> {
        val store = openStore()
        store.edit { it[key] = "\"TURBO\"" }

        GeneralSettings(store, json).backgroundSyncLevel.value() shouldBe null
    }

    @Test
    fun `content that is not JSON reads as never chosen`() = runBlocking<Unit> {
        val store = openStore()
        store.edit { it[key] = "{not json" }

        GeneralSettings(store, json).backgroundSyncLevel.value() shouldBe null
    }

    companion object {
        private var storeCounter = 0
    }
}
