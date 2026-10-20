package eu.darken.apl.main.core

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import eu.darken.apl.common.datastore.value
import eu.darken.apl.common.serialization.SerializationModule
import eu.darken.apl.common.theming.ThemePalette
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.io.File
import java.util.Collections

class GeneralSettingsThemeTest : BaseTest() {

    private val json = SerializationModule().json()

    private lateinit var storeFile: File
    private val storeScopes = mutableListOf<CoroutineScope>()

    @BeforeEach
    fun setup() {
        storeFile = File(IO_TEST_BASEDIR, "general-settings-${storeCounter++}.preferences_pb")
        storeFile.parentFile?.mkdirs()
        storeFile.delete()
    }

    @AfterEach
    fun teardown() {
        runBlocking { storeScopes.forEach { it.coroutineContext.job.cancelAndJoin() } }
        storeScopes.clear()
        storeFile.delete()
    }

    // A DataStore keeps its file claimed until its scope is done
    private fun openStore(): Pair<CoroutineScope, DataStore<Preferences>> {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also { storeScopes.add(it) }
        return scope to PreferenceDataStoreFactory.create(scope = scope, produceFile = { storeFile })
    }

    private suspend fun closeStore(scope: CoroutineScope) {
        scope.coroutineContext.job.cancelAndJoin()
        storeScopes.remove(scope)
    }

    @Test
    fun `custom seed and palette start unset`() = runBlocking<Unit> {
        val (_, store) = openStore()
        val settings = GeneralSettings(store, json)

        settings.themeCustomSeed.value() shouldBe null
        settings.themeCustomPalette.value() shouldBe ThemePalette.TONAL_SPOT

        settings.themeState.first().apply {
            customSeed shouldBe null
            customPalette shouldBe ThemePalette.TONAL_SPOT
        }
    }

    @Test
    fun `setting the custom theme stores seed and palette`() = runBlocking<Unit> {
        val (_, store) = openStore()
        val settings = GeneralSettings(store, json)

        settings.setThemeCustom(0xE53935, ThemePalette.VIBRANT)

        settings.themeCustomSeed.value() shouldBe 0xE53935
        settings.themeCustomPalette.value() shouldBe ThemePalette.VIBRANT

        store.data.first().apply {
            this[intPreferencesKey("core.ui.theme.custom.seed")] shouldBe 0xE53935
            this[stringPreferencesKey("core.ui.theme.custom.palette")] shouldBe "\"VIBRANT\""
        }

        settings.themeState.first().apply {
            customSeed shouldBe 0xE53935
            customPalette shouldBe ThemePalette.VIBRANT
        }
    }

    @Test
    fun `resetting the custom theme clears the seed`() = runBlocking<Unit> {
        val (_, store) = openStore()
        val settings = GeneralSettings(store, json)

        settings.setThemeCustom(0xE53935, ThemePalette.VIBRANT)
        settings.setThemeCustom(null, ThemePalette.TONAL_SPOT)

        settings.themeCustomSeed.value() shouldBe null
        settings.themeCustomPalette.value() shouldBe ThemePalette.TONAL_SPOT
        store.data.first()[intPreferencesKey("core.ui.theme.custom.seed")] shouldBe null
    }

    @Test
    fun `custom theme survives reopening the store`() = runBlocking<Unit> {
        val (firstScope, firstStore) = openStore()
        GeneralSettings(firstStore, json).setThemeCustom(0x2D638E, ThemePalette.FRUIT_SALAD)
        closeStore(firstScope)

        val (_, secondStore) = openStore()
        val reopened = GeneralSettings(secondStore, json)

        reopened.themeCustomSeed.value() shouldBe 0x2D638E
        reopened.themeCustomPalette.value() shouldBe ThemePalette.FRUIT_SALAD
    }

    @Test
    fun `readers never see a new seed with the old palette`() = runBlocking<Unit> {
        val (_, store) = openStore()
        val settings = GeneralSettings(store, json)

        val first = 0x111111 to ThemePalette.NEUTRAL
        val second = 0x222222 to ThemePalette.VIBRANT
        val consistent = setOf<Pair<Int?, ThemePalette>>(null to ThemePalette.TONAL_SPOT, first, second)

        val observed = Collections.synchronizedList(mutableListOf<Pair<Int?, ThemePalette>>())
        val collector = launch(Dispatchers.Unconfined) {
            store.data.collect { prefs ->
                observed.add(
                    settings.themeCustomSeed.reader(prefs[settings.themeCustomSeed.key])
                        to settings.themeCustomPalette.reader(prefs[settings.themeCustomPalette.key])
                )
            }
        }

        withTimeout(5_000) { while (observed.isEmpty()) delay(5) }

        repeat(10) { round ->
            val target = if (round % 2 == 0) first else second
            settings.setThemeCustom(target.first, target.second)
            withTimeout(5_000) { while (observed.last() != target) delay(5) }
        }

        collector.cancelAndJoin()

        withClue("observed $observed") {
            observed.all { it in consistent } shouldBe true
        }
    }

    @Test
    fun `an unknown stored palette reads as tonal spot`() = runBlocking<Unit> {
        val (_, store) = openStore()
        store.edit { it[stringPreferencesKey("core.ui.theme.custom.palette")] = "\"PLAID\"" }
        val settings = GeneralSettings(store, json)

        settings.themeCustomPalette.value() shouldBe ThemePalette.TONAL_SPOT
    }

    companion object {
        private var storeCounter = 0
    }
}
