package eu.darken.apl.server.compat

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import eu.darken.apl.common.serialization.SerializationModule
import eu.darken.apl.server.api.MetaResponse
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import testhelper.TestCompatibilityStore
import java.io.IOException
import java.time.Instant

class AppCompatibilityTest : BaseTest() {

    private val json = SerializationModule().json()
    private val compatStore = TestCompatibilityStore()

    @AfterEach
    fun teardown() {
        compatStore.close()
    }

    private fun TestScope.holder(dataStore: DataStore<Preferences>) = AppCompatibility(
        appScope = backgroundScope,
        dataStore = dataStore,
        json = json,
        versionCode = VERSION,
    )

    private suspend fun storeSnapshot(raw: String) {
        compatStore.dataStore.edit { it[SNAPSHOT_KEY] = raw }
    }

    private suspend fun storedSnapshot(): String? = compatStore.dataStore.data.first()[SNAPSHOT_KEY]

    @Test
    fun `the state is unknown until the stored answer is read`() = runTest {
        val dataStore = FakeDataStore().apply { readGate = CompletableDeferred() }
        val compatibility = holder(dataStore)
        runCurrent()

        compatibility.state.value shouldBe CompatState.Unknown

        dataStore.readGate!!.complete(Unit)
        compatibility.awaitLoaded()
        compatibility.state.value shouldBe CompatState.Ok
    }

    @Test
    fun `nothing stored is compatible`() = runTest {
        val compatibility = compatStore.holder(VERSION)

        compatibility.awaitLoaded()

        compatibility.state.value shouldBe CompatState.Ok
        compatibility.lastCheckedAt().shouldBeNull()
    }

    @Test
    fun `a stored refusal for this build survives a restart`() = runTest {
        storeSnapshot("""{"appVersionCode":$VERSION,"result":"REQUIRED","checkedAt":"2026-01-01T00:00:00Z"}""")

        val compatibility = compatStore.holder(VERSION)
        compatibility.awaitLoaded()

        compatibility.state.value shouldBe CompatState.UpdateRequired
        compatibility.lastCheckedAt() shouldBe CHECKED_AT
    }

    @Test
    fun `a stored recommendation for this build survives a restart`() = runTest {
        storeSnapshot("""{"appVersionCode":$VERSION,"result":"RECOMMENDED","recommendedVersion":200}""")

        val compatibility = compatStore.holder(VERSION)
        compatibility.awaitLoaded()

        compatibility.state.value shouldBe CompatState.UpdateRecommended(200)
        compatibility.lastCheckedAt().shouldBeNull()
    }

    @Test
    fun `a refusal stored by an older build is dropped`() = runTest {
        storeSnapshot("""{"appVersionCode":${VERSION - 1},"result":"REQUIRED","checkedAt":"2026-01-01T00:00:00Z"}""")

        val compatibility = compatStore.holder(VERSION)
        compatibility.awaitLoaded()

        compatibility.state.value shouldBe CompatState.Ok
        compatibility.lastCheckedAt().shouldBeNull()
        storedSnapshot().shouldBeNull()
    }

    @Test
    fun `requirements below the minimum require an update`() = runTest {
        val compatibility = compatStore.holder(VERSION)

        compatibility.applyMeta(compatibility.beginCheck(), meta(min = VERSION + 1, recommended = VERSION + 10), CHECKED_AT) shouldBe true

        compatibility.state.value shouldBe CompatState.UpdateRequired
    }

    @Test
    fun `requirements below the recommendation recommend an update`() = runTest {
        val compatibility = compatStore.holder(VERSION)

        compatibility.applyMeta(compatibility.beginCheck(), meta(min = VERSION, recommended = VERSION + 10), CHECKED_AT) shouldBe true

        compatibility.state.value shouldBe CompatState.UpdateRecommended(VERSION.toInt() + 10)
    }

    @Test
    fun `requirements this build meets are compatible`() = runTest {
        val compatibility = compatStore.holder(VERSION)
        compatibility.noteRefusal()

        compatibility.applyMeta(compatibility.beginCheck(), meta(min = VERSION, recommended = VERSION), CHECKED_AT) shouldBe true

        compatibility.state.value shouldBe CompatState.Ok
    }

    @Test
    fun `applied requirements are stored with the time they were read`() = runTest {
        val compatibility = compatStore.holder(VERSION)

        compatibility.applyMeta(compatibility.beginCheck(), meta(min = 0, recommended = 200), CHECKED_AT)

        compatibility.lastCheckedAt() shouldBe CHECKED_AT
        storedSnapshot() shouldBe
                """{"appVersionCode":$VERSION,"result":"RECOMMENDED","recommendedVersion":200,"checkedAt":"2026-01-01T00:00:00Z"}"""

        val restarted = compatStore.holder(VERSION)
        restarted.awaitLoaded()
        restarted.state.value shouldBe CompatState.UpdateRecommended(200)
        restarted.lastCheckedAt() shouldBe CHECKED_AT
    }

    @Test
    fun `a refusal keeps the time the requirements were last read`() = runTest {
        val compatibility = compatStore.holder(VERSION)
        compatibility.applyMeta(compatibility.beginCheck(), meta(min = 0, recommended = 0), CHECKED_AT)

        compatibility.noteRefusal()

        compatibility.lastCheckedAt() shouldBe CHECKED_AT
        val restarted = compatStore.holder(VERSION)
        restarted.awaitLoaded()
        restarted.state.value shouldBe CompatState.UpdateRequired
        restarted.lastCheckedAt() shouldBe CHECKED_AT
    }

    @Test
    fun `requirements read before a refusal cannot lift it`() = runTest {
        val compatibility = compatStore.holder(VERSION)
        val generation = compatibility.beginCheck()

        compatibility.noteRefusal()

        compatibility.applyMeta(generation, meta(min = 0, recommended = 0), CHECKED_AT) shouldBe false
        compatibility.state.value shouldBe CompatState.UpdateRequired
        compatibility.lastCheckedAt().shouldBeNull()
    }

    @Test
    fun `requirements read after a refusal lift it`() = runTest {
        val compatibility = compatStore.holder(VERSION)
        compatibility.noteRefusal()
        val generation = compatibility.beginCheck()

        compatibility.applyMeta(generation, meta(min = 0, recommended = 0), CHECKED_AT) shouldBe true

        compatibility.state.value shouldBe CompatState.Ok
    }

    @Test
    fun `a store that cannot be read is treated as compatible`() = runTest {
        val compatibility = holder(FakeDataStore().apply { failReads = true })

        compatibility.awaitLoaded()

        compatibility.state.value shouldBe CompatState.Ok
        compatibility.lastCheckedAt().shouldBeNull()
    }

    @Test
    fun `a refusal that cannot be stored still applies`() = runTest {
        val compatibility = holder(FakeDataStore().apply { failWrites = true })

        compatibility.noteRefusal()

        compatibility.state.value shouldBe CompatState.UpdateRequired
    }

    @Test
    fun `requirements that cannot be stored still apply`() = runTest {
        val compatibility = holder(FakeDataStore().apply { failWrites = true })

        compatibility.applyMeta(compatibility.beginCheck(), meta(min = VERSION + 1, recommended = 0), CHECKED_AT) shouldBe true

        compatibility.state.value shouldBe CompatState.UpdateRequired
        compatibility.lastCheckedAt() shouldBe CHECKED_AT
    }

    @Test
    fun `a dismissed recommendation is stored apart from the requirements`() = runTest {
        val compatibility = compatStore.holder(VERSION)
        compatibility.applyMeta(compatibility.beginCheck(), meta(min = 0, recommended = 200), CHECKED_AT)
        compatibility.dismissedRecommendation.first().shouldBeNull()

        compatibility.dismissRecommendation(200)

        compatibility.dismissedRecommendation.first() shouldBe 200
        compatibility.state.value shouldBe CompatState.UpdateRecommended(200)

        compatibility.applyMeta(compatibility.beginCheck(), meta(min = 0, recommended = 0), CHECKED_AT)
        compatibility.dismissedRecommendation.first() shouldBe 200

        val restarted = compatStore.holder(VERSION)
        restarted.dismissedRecommendation.first() shouldBe 200
        restarted.awaitLoaded()
        restarted.state.value shouldBe CompatState.Ok
    }

    private fun meta(min: Long, recommended: Long) = MetaResponse(
        recommendedAppVersion = recommended.toInt(),
        minSupportedAppVersion = min.toInt(),
    )

    private class FakeDataStore : DataStore<Preferences> {
        var readGate: CompletableDeferred<Unit>? = null
        var failReads = false
        var failWrites = false
        private val prefs = MutableStateFlow(emptyPreferences())

        override val data: Flow<Preferences> = flow {
            readGate?.await()
            if (failReads) throw IOException("read failed")
            emitAll(prefs)
        }

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            if (failWrites) throw IOException("write failed")
            return transform(prefs.value).also { prefs.value = it }
        }
    }

    companion object {
        private const val VERSION = 100L
        private val CHECKED_AT = Instant.parse("2026-01-01T00:00:00Z")
        private val SNAPSHOT_KEY = stringPreferencesKey("compat.snapshot")
    }
}
