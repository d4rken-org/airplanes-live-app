package eu.darken.apl.upgrade

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class UpgradeRepoExtensionsTest : BaseTest() {

    private class TestRepo(private val infos: Flow<UpgradeRepo.Info>) : UpgradeRepo {
        override val upgradeInfo = infos
        override suspend fun refresh() = Unit
    }

    private data class Info(
        override val isPro: Boolean,
        override val isSettled: Boolean,
        override val type: UpgradeRepo.Type = UpgradeRepo.Type.FOSS,
        override val source: UpgradeRepo.Source? = null,
        override val error: Throwable? = null,
    ) : UpgradeRepo.Info

    @Test
    fun `an unsettled first emission answers free without waiting`() = runTest {
        val repo = TestRepo(
            flow {
                emit(Info(isPro = false, isSettled = false))
                // A settled emission may never arrive, isProNow() must not be held up by that
                awaitCancellation()
            }
        )

        withTimeoutOrNull(60_000) { repo.isProNow() } shouldBe false
    }

    @Test
    fun `a settled pro emission answers pro`() = runTest {
        val repo = TestRepo(flow { emit(Info(isPro = true, isSettled = true)) })

        repo.isProNow() shouldBe true
    }

    @Test
    fun `a settled free emission answers free`() = runTest {
        val repo = TestRepo(flow { emit(Info(isPro = false, isSettled = true)) })

        repo.isProNow() shouldBe false
    }
}
