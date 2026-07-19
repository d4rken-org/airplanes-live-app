package eu.darken.apl.feeder.core

import eu.darken.apl.account.core.AccountRepo
import eu.darken.apl.account.core.SessionExpiredException
import eu.darken.apl.account.core.api.AccountApi
import eu.darken.apl.account.core.api.AccountEndpoint
import eu.darken.apl.common.datastore.DataStoreValue
import eu.darken.apl.feeder.core.config.FeederSettings
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class FeederRepoTest : BaseTest() {

    private fun identity(id: Long) = AccountApi.Identity(id = id)

    private fun feeder(id: String) = Feeder(id = id, status = FeederStatus.ACTIVE)

    private fun createRepo(
        scope: kotlinx.coroutines.CoroutineScope,
        loggedIn: Boolean,
        identity: AccountApi.Identity?,
        cache: FeederCache,
    ): FeederRepo {
        val accountRepo = mockk<AccountRepo> {
            every { isLoggedIn } returns flowOf(loggedIn)
            every { this@mockk.identity } returns flowOf(identity)
        }
        val cacheValue = mockk<DataStoreValue<FeederCache>>(relaxed = true) {
            every { flow } returns flowOf(cache)
        }
        val feederSettings = mockk<FeederSettings>(relaxed = true) {
            every { feederCache } returns cacheValue
        }
        return FeederRepo(
            appScope = scope,
            accountRepo = accountRepo,
            accountEndpoint = mockk<AccountEndpoint>(relaxed = true),
            feederSettings = feederSettings,
        )
    }

    @Test
    fun `feeders are empty when logged out`() = runTest {
        val repo = createRepo(
            scope = backgroundScope,
            loggedIn = false,
            identity = identity(42),
            cache = FeederCache(ownerId = 42, feeders = listOf(feeder("a"))),
        )
        repo.feeders.first() shouldBe emptyList()
    }

    @Test
    fun `feeders are shown when logged in and cache owner matches`() = runTest {
        val repo = createRepo(
            scope = backgroundScope,
            loggedIn = true,
            identity = identity(42),
            cache = FeederCache(ownerId = 42, feeders = listOf(feeder("a"), feeder("b"))),
        )
        repo.feeders.first().map { it.id } shouldBe listOf("a", "b")
    }

    @Test
    fun `feeders are hidden when cache belongs to a different account`() = runTest {
        val repo = createRepo(
            scope = backgroundScope,
            loggedIn = true,
            identity = identity(99), // logged in as 99
            cache = FeederCache(ownerId = 42, feeders = listOf(feeder("a"))), // cache from account 42
        )
        repo.feeders.first() shouldBe emptyList()
    }

    @Test
    fun `feeders are hidden when identity not yet known`() = runTest {
        val repo = createRepo(
            scope = backgroundScope,
            loggedIn = true,
            identity = null,
            cache = FeederCache(ownerId = 42, feeders = listOf(feeder("a"))),
        )
        repo.feeders.first() shouldBe emptyList()
    }

    @Test
    fun `session expiry during refresh clears the cache but does not call logout`() = runTest {
        val accountRepo = mockk<AccountRepo>(relaxed = true) {
            every { isLoggedIn } returns flowOf(true)
            every { identity } returns flowOf(identity(42))
        }
        val cacheValue = mockk<DataStoreValue<FeederCache>>(relaxed = true) {
            every { flow } returns flowOf(FeederCache(ownerId = 42, feeders = listOf(feeder("a"))))
        }
        val feederSettings = mockk<FeederSettings>(relaxed = true) {
            every { feederCache } returns cacheValue
        }
        val endpoint = mockk<AccountEndpoint> {
            coEvery { getOwnedFeeders() } throws SessionExpiredException()
        }
        val repo = FeederRepo(
            appScope = backgroundScope,
            accountRepo = accountRepo,
            accountEndpoint = endpoint,
            feederSettings = feederSettings,
        )

        repo.refresh()

        // AuthManager already invalidated the session when throwing; a logout() here could revoke
        // a newer login racing this refresh.
        coVerify(exactly = 0) { accountRepo.logout() }
        coVerify { cacheValue.update(any()) }
    }
}
