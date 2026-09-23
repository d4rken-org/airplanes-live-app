package eu.darken.apl.search.core

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import eu.darken.apl.common.datastore.createValue
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
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.io.File

class SearchInputTest : BaseTest() {

    private val json = SerializationModule().json()
    private val testFile = File(IO_TEST_BASEDIR, SearchInputTest::class.java.simpleName + ".preferences_pb")

    // A DataStore keeps its file claimed until its scope is done
    private val storeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private fun createDataStore() = PreferenceDataStoreFactory.create(
        scope = storeScope,
        produceFile = { testFile },
    )

    @AfterEach
    fun tearDown() {
        runBlocking { storeScope.coroutineContext.job.cancelAndJoin() }
        testFile.delete()
    }

    @Test
    fun `stored format`() {
        val input = SearchInput(
            text = "DLH453 A320",
            categories = setOf(SearchCategory.MILITARY, SearchCategory.PIA),
            nearby = true,
            place = "Frankfurt am Main",
        )
        val stored = """{"text":"DLH453 A320","categories":["military","pia"],"nearby":true,"place":"Frankfurt am Main"}"""

        json.encodeToString(SearchInput.serializer(), input) shouldBe stored
        json.decodeFromString(SearchInput.serializer(), stored) shouldBe input
    }

    @Test
    fun `missing fields take their defaults`() {
        json.decodeFromString(SearchInput.serializer(), """{"text":"7700"}""") shouldBe SearchInput(text = "7700")
    }

    @Test
    fun `an unreadable stored value falls back to an empty input`() {
        runBlocking {
            val store = createDataStore()
            val key = "search.query"
            store.edit { it[stringPreferencesKey(key)] = """{"categories":["interesting"]}""" }

            val value = store.createValue(key, SearchInput(), json, onErrorFallbackToDefault = true)

            value.flow.first() shouldBe SearchInput()

            value.value(SearchInput(text = "A320"))
            value.value() shouldBe SearchInput(text = "A320")
        }
    }
}
