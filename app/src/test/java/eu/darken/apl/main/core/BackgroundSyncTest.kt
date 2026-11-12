package eu.darken.apl.main.core

import eu.darken.apl.common.datastore.DataStoreValue
import eu.darken.apl.upgrade.UpgradeRepo
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class BackgroundSyncTest : BaseTest() {

    private class TestRepo(override val upgradeInfo: Flow<UpgradeRepo.Info>) : UpgradeRepo {
        override suspend fun refresh() = Unit
    }

    private data class Info(
        override val isPro: Boolean,
        override val isSettled: Boolean,
        override val type: UpgradeRepo.Type = UpgradeRepo.Type.FOSS,
        override val source: UpgradeRepo.Source? = null,
        override val error: Throwable? = null,
    ) : UpgradeRepo.Info

    private fun backgroundSync(stored: Flow<BackgroundSyncLevel?>, infos: Flow<UpgradeRepo.Info>): BackgroundSync {
        val value = mockk<DataStoreValue<BackgroundSyncLevel?>>().apply {
            every { flow } returns stored
        }
        val settings = mockk<GeneralSettings>().apply {
            every { backgroundSyncLevel } returns value
        }
        return BackgroundSync(settings, TestRepo(infos))
    }

    @Test
    fun `a stored level is the first value`() = runTest {
        val sync = backgroundSync(flowOf(BackgroundSyncLevel.SLOW), flowOf(Info(isPro = false, isSettled = true)))

        sync.level.first() shouldBe BackgroundSyncLevel.SLOW
    }

    @Test
    fun `an unsettled tier counts as free without waiting for a settled one`() = runTest {
        val infos = flow<UpgradeRepo.Info> {
            emit(Info(isPro = true, isSettled = false))
            // A settled emission may never arrive
            awaitCancellation()
        }
        val sync = backgroundSync(flowOf(BackgroundSyncLevel.FAST), infos)

        withTimeoutOrNull(60_000) { sync.level.first() } shouldBe BackgroundSyncLevel.BALANCED
    }

    @Test
    fun `a tier change emits the new level`() = runTest {
        val infos = MutableStateFlow<UpgradeRepo.Info>(Info(isPro = false, isSettled = true))
        val sync = backgroundSync(flowOf(null), infos)
        val levels = mutableListOf<BackgroundSyncLevel>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { sync.level.collect { levels += it } }

        infos.value = Info(isPro = true, isSettled = true)
        runCurrent()
        infos.value = Info(isPro = false, isSettled = true)
        runCurrent()

        levels shouldBe listOf(BackgroundSyncLevel.BALANCED, BackgroundSyncLevel.FAST, BackgroundSyncLevel.BALANCED)
    }

    @Test
    fun `an equal level is not emitted again`() = runTest {
        val infos = MutableStateFlow<UpgradeRepo.Info>(Info(isPro = false, isSettled = true))
        val sync = backgroundSync(flowOf(BackgroundSyncLevel.SLOW), infos)
        val levels = mutableListOf<BackgroundSyncLevel>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { sync.level.collect { levels += it } }

        infos.value = Info(isPro = true, isSettled = true)
        runCurrent()

        levels shouldBe listOf(BackgroundSyncLevel.SLOW)
    }
}
