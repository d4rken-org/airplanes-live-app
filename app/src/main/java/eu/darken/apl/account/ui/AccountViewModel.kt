package eu.darken.apl.account.ui

import android.content.Intent
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.apl.account.core.AccountRepo
import eu.darken.apl.account.core.LoginCancelledException
import eu.darken.apl.account.core.api.AccountApi
import eu.darken.apl.account.core.auth.AuthManager
import eu.darken.apl.common.BuildConfigWrap
import eu.darken.apl.common.WebpageTool
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.uix.ViewModel4
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
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
