package eu.darken.apl.account.ui

import android.content.Intent
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.apl.account.core.AccountRepo
import eu.darken.apl.account.core.LoginCancelledException
import eu.darken.apl.account.core.SessionExpiredException
import eu.darken.apl.account.core.api.AccountApi
import eu.darken.apl.account.core.auth.AuthManager
import eu.darken.apl.common.BuildConfigWrap
import eu.darken.apl.common.WebpageTool
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.uix.ViewModel4
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@HiltViewModel
class AccountViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val accountRepo: AccountRepo,
    private val authManager: AuthManager,
    private val webpageTool: WebpageTool,
) : ViewModel4(
    dispatcherProvider = dispatcherProvider,
    tag = logTag("Account", "ViewModel"),
) {

    private val busy = MutableStateFlow(false)

    init {
        // The website is the source of truth for the profile; refresh once per screen-open so a
        // display name changed there doesn't stay stale until the next re-login. Best-effort:
        // the cached identity is a fine fallback, so failures are logged, never surfaced. A 401
        // here already invalidates the session via AccountEndpoint, flipping the UI to logged-out.
        launch {
            if (!accountRepo.isLoggedIn.first()) return@launch
            try {
                accountRepo.refreshIdentity()
            } catch (e: Exception) {
                log(tag, WARN) { "On-open identity refresh failed: ${e.asLog()}" }
            }
        }
    }

    val state = combine(
        accountRepo.isLoggedIn,
        accountRepo.identity,
        busy,
    ) { loggedIn, identity, busy ->
        State(isLoggedIn = loggedIn, identity = identity, isBusy = busy)
    }.asStateFlow()

    /** Synchronous: produces the AppAuth Custom Tab intent for an ActivityResult launcher. */
    fun buildLoginIntent(): Intent = authManager.createAuthRequestIntent()

    fun onLoginResult(data: Intent?) = launch {
        log(tag) { "onLoginResult(data=$data)" }
        if (data == null) return@launch // result with no payload (e.g. activity destroyed)
        busy.value = true
        try {
            accountRepo.completeLogin(data)
        } catch (e: LoginCancelledException) {
            log(tag) { "Login cancelled by user, not surfacing as an error." }
        } catch (e: SessionExpiredException) {
            // Revoked between authorize and identity fetch: the session was already cleared, so
            // fall back to the logged-out state quietly rather than showing an error dialog.
            log(tag) { "Session expired while completing login, treating as silent logout." }
        } finally {
            busy.value = false
        }
    }

    fun logout() = launch {
        log(tag) { "logout()" }
        busy.value = true
        try {
            accountRepo.logout()
        } finally {
            busy.value = false
        }
    }

    fun openManageAccount() = launch {
        webpageTool.open(BuildConfigWrap.OAUTH_HOST)
    }

    data class State(
        val isLoggedIn: Boolean,
        val identity: AccountApi.Identity?,
        val isBusy: Boolean,
    )
}
