package eu.darken.apl.main.core.update

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import eu.darken.apl.common.serialization.SerializationModule
import eu.darken.apl.main.core.update.AppUpdatePrompt.Prompt
import eu.darken.apl.server.api.MetaResponse
import eu.darken.apl.server.compat.AppCompatibility
import eu.darken.apl.server.compat.CompatState
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import testhelper.TestCompatibilityStore
import java.io.IOException
import java.time.Instant

class AppUpdatePromptTest : BaseTest() {

    private val compatStore = TestCompatibilityStore()

    @AfterEach
    fun teardown() {
        compatStore.close()
    }

    private suspend fun loadedHolder(): AppCompatibility = compatStore.holder(VERSION).apply { awaitLoaded() }

    private suspend fun AppCompatibility.serverSays(min: Int, recommended: Int) {
        applyMeta(
            generation = beginCheck(),
            meta = MetaResponse(recommendedAppVersion = recommended, minSupportedAppVersion = min),
            checkedAt = Instant.parse("2026-01-01T00:00:00Z"),
        )
    }

    @Test
    fun `no prompt while compatible`() = runTest {
        val compatibility = loadedHolder()
        compatibility.serverSays(min = 50, recommended = 100)

        AppUpdatePrompt(compatibility).prompt.first().shouldBeNull()
    }

    @Test
    fun `says nothing before the stored answer is read`() = runTest {
        val compatState = MutableStateFlow<CompatState>(CompatState.Unknown)
        val compatibility = mockk<AppCompatibility> {
            every { state } returns compatState
            every { dismissedRecommendation } returns flowOf(null)
        }
        val emitted = mutableListOf<Prompt?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            AppUpdatePrompt(compatibility).prompt.toList(emitted)
        }

        emitted.shouldBeEmpty()

        compatState.value = CompatState.UpdateRequired

        emitted shouldBe listOf(Prompt.Required)
    }

    @Test
    fun `a stored answer without a prompt says so once read`() = runTest {
        val compatState = MutableStateFlow<CompatState>(CompatState.Unknown)
        val compatibility = mockk<AppCompatibility> {
            every { state } returns compatState
            every { dismissedRecommendation } returns flowOf(null)
        }
        val emitted = mutableListOf<Prompt?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            AppUpdatePrompt(compatibility).prompt.toList(emitted)
        }

        compatState.value = CompatState.Ok

        emitted shouldBe listOf(null)
    }

    @Test
    fun `a refused app asks until the user continues`() = runTest {
        val compatibility = loadedHolder()
        compatibility.noteRefusal()
        val updatePrompt = AppUpdatePrompt(compatibility)

        updatePrompt.prompt.first() shouldBe Prompt.Required

        updatePrompt.continueLimited()

        updatePrompt.prompt.first().shouldBeNull()
    }

    @Test
    fun `continuing lasts only for this process`() = runTest {
        val compatibility = loadedHolder()
        compatibility.serverSays(min = 150, recommended = 150)
        AppUpdatePrompt(compatibility).continueLimited()

        AppUpdatePrompt(compatibility).prompt.first() shouldBe Prompt.Required
    }

    @Test
    fun `a recommendation asks until put off`() = runTest {
        val compatibility = loadedHolder()
        compatibility.serverSays(min = 50, recommended = 110)
        val updatePrompt = AppUpdatePrompt(compatibility)

        updatePrompt.prompt.first() shouldBe Prompt.Recommended(110)

        updatePrompt.notNow(110)

        updatePrompt.prompt.first().shouldBeNull()
    }

    @Test
    fun `putting off a recommendation survives a restart`() = runTest {
        compatStore.holder(VERSION).apply {
            awaitLoaded()
            serverSays(min = 50, recommended = 110)
        }
        AppUpdatePrompt(loadedHolder()).notNow(110)

        AppUpdatePrompt(loadedHolder()).prompt.first().shouldBeNull()
    }

    @Test
    fun `putting off a recommendation that cannot be stored holds for this process`() = runTest {
        val compatibility = AppCompatibility(
            appScope = backgroundScope,
            dataStore = FailingWritesDataStore(),
            json = SerializationModule().json(),
            versionCode = VERSION,
        ).apply { awaitLoaded() }
        compatibility.serverSays(min = 50, recommended = 110)
        val updatePrompt = AppUpdatePrompt(compatibility)

        updatePrompt.notNow(110)

        updatePrompt.prompt.first().shouldBeNull()
        compatibility.dismissedRecommendation.first().shouldBeNull()
        AppUpdatePrompt(compatibility).prompt.first() shouldBe Prompt.Recommended(110)
    }

    @Test
    fun `a higher recommendation asks again`() = runTest {
        val compatibility = loadedHolder()
        compatibility.serverSays(min = 50, recommended = 110)
        val updatePrompt = AppUpdatePrompt(compatibility)
        updatePrompt.notNow(110)

        compatibility.serverSays(min = 50, recommended = 120)

        updatePrompt.prompt.first() shouldBe Prompt.Recommended(120)
    }

    @Test
    fun `putting off a recommendation is stored even when the caller is cancelled`() = runTest {
        val gated = GatedWritesDataStore(compatStore.dataStore)
        val compatibility = AppCompatibility(
            appScope = backgroundScope,
            dataStore = gated,
            json = SerializationModule().json(),
            versionCode = VERSION,
        ).apply { awaitLoaded() }
        compatibility.serverSays(min = 50, recommended = 110)
        val updatePrompt = AppUpdatePrompt(compatibility)
        updatePrompt.prompt.first() shouldBe Prompt.Recommended(110)
        val gate = CompletableDeferred<Unit>()
        gated.gate = gate

        val caller = launch { updatePrompt.notNow(110) }
        updatePrompt.prompt.first { it == null }
        caller.cancel()
        gate.complete(Unit)
        advanceUntilIdle()
        withContext(Dispatchers.Default) { withTimeout(5_000) { gated.gatedWriteSettled.await() } }

        compatStore.holder(VERSION).apply { awaitLoaded() }.dismissedRecommendation.first() shouldBe 110
    }

    /** Writes made while [gate] is set wait for it; [gatedWriteSettled] completes once such a write ends either way. */
    private class GatedWritesDataStore(private val delegate: DataStore<Preferences>) : DataStore<Preferences> {
        @Volatile var gate: CompletableDeferred<Unit>? = null
        val gatedWriteSettled = CompletableDeferred<Unit>()

        override val data: Flow<Preferences> = delegate.data

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val gate = gate ?: return delegate.updateData(transform)
            try {
                gate.await()
                return delegate.updateData(transform)
            } finally {
                gatedWriteSettled.complete(Unit)
            }
        }
    }

    private class FailingWritesDataStore : DataStore<Preferences> {
        override val data: Flow<Preferences> = flowOf(emptyPreferences())

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            throw IOException("write failed")
        }
    }

    companion object {
        private const val VERSION = 100L
    }
}
