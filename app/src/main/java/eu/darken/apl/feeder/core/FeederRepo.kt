package eu.darken.apl.feeder.core

import eu.darken.apl.account.core.AccountRepo
import eu.darken.apl.account.core.SessionExpiredException
import eu.darken.apl.account.core.api.AccountApi
import eu.darken.apl.account.core.api.AccountEndpoint
import eu.darken.apl.common.coroutine.AppScope
import eu.darken.apl.common.datastore.value
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.feeder.core.config.FeederPosition
import eu.darken.apl.feeder.core.config.FeederSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeederRepo @Inject constructor(
    @param:AppScope private val appScope: CoroutineScope,
    private val accountRepo: AccountRepo,
    private val accountEndpoint: AccountEndpoint,
    private val feederSettings: FeederSettings,
) {

    val isLoggedIn: Flow<Boolean> = accountRepo.isLoggedIn
    val isRefreshing = MutableStateFlow(false)
    private val refreshLock = Mutex()

    val feeders: Flow<List<Feeder>> = combine(
        accountRepo.isLoggedIn,
        accountRepo.identity,
        feederSettings.feederCache.flow,
    ) { loggedIn, identity, cache ->
        if (loggedIn && cache.ownerId != null && cache.ownerId == identity?.id) cache.feeders else emptyList()
    }

    init {
        // Session cleared (logout / dead token family) -> drop the cached feeders. A false->true
        // transition is a fresh login in this process -> fetch right away so the list doesn't sit
        // empty until a manual pull-to-refresh or the periodic worker. The first emission is
        // deliberately not a trigger (app start refresh is handled by the worker and the list UI).
        var wasLoggedIn: Boolean? = null
        accountRepo.isLoggedIn
            .onEach { loggedIn ->
                when {
                    // Under the lock so an in-flight refresh can't finish afterwards and write the
                    // logged-out account's feeders back into the cleared cache.
                    !loggedIn -> refreshLock.withLock { feederSettings.feederCache.value(FeederCache()) }
                    wasLoggedIn == false -> try {
                        refresh()
                    } catch (e: Exception) {
                        // Never let a failed refresh kill this collector (or the app via the
                        // handler-less AppScope) — the worker and manual refresh remain as retries.
                        log(TAG, WARN) { "Post-login feeder refresh failed: ${e.asLog()}" }
                    }
                }
                wasLoggedIn = loggedIn
            }
            .launchIn(appScope)
    }

    suspend fun refresh() = refreshLock.withLock {
        log(TAG) { "refresh()" }
        if (!accountRepo.isLoggedIn.first()) {
            feederSettings.feederCache.value(FeederCache())
            return@withLock
        }

        isRefreshing.value = true
        try {
            val identity = accountRepo.identity.first()
                ?: run { accountRepo.refreshIdentity(); accountRepo.identity.first() }
            val owned = accountEndpoint.getOwnedFeeders().map { it.toFeeder() }
            feederSettings.feederCache.value(FeederCache(ownerId = identity?.id, feeders = owned))
            feederSettings.lastRefreshAt.value(Instant.now())
        } catch (e: SessionExpiredException) {
            // AuthManager has already cleared the session when this is thrown. Calling logout()
            // here would race a concurrent re-login: it unconditionally clears + revokes whatever
            // session is stored by then, which may be the *new* one. Only drop our own cache.
            log(TAG, WARN) { "Session expired during refresh: ${e.asLog()}" }
            feederSettings.feederCache.value(FeederCache())
        } finally {
            isRefreshing.value = false
        }
    }

    private fun AccountApi.OwnedFeeder.toFeeder() = Feeder(
        id = feederId,
        name = name,
        status = FeederStatus.fromApi(status),
        lastSeen = lastSeen,
        country = country,
        position = position?.let { FeederPosition(latitude = it.lat, longitude = it.lon) },
        lastSeenIp = lastSeenIp,
        firstSeen = firstSeen,
    )

    companion object {
        private val TAG = logTag("Feeder", "Repo")
    }
}
