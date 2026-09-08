package eu.darken.apl.main.ui.settings.access

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.apl.common.BuildConfigWrap
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.flow.combine
import eu.darken.apl.common.uix.ViewModel4
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.access.AccessState
import eu.darken.apl.server.session.SessionManager
import eu.darken.apl.server.session.SessionState
import javax.inject.Inject

@HiltViewModel
class AccessStatusViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val accessRepo: AccessRepo,
    private val sessionManager: SessionManager,
) : ViewModel4(
    dispatcherProvider = dispatcherProvider,
    tag = logTag("Settings", "Access", "VM"),
) {

    data class State(
        val access: AccessState? = null,
        val installationId: String? = null,
        val canResetIdentity: Boolean = BuildConfigWrap.DEBUG,
    )

    val state = combine(
        accessRepo.state,
        sessionManager.state,
    ) { access, session ->
        State(
            access = access,
            installationId = (session as? SessionState.Active)?.installationId,
        )
    }.asStateFlow()

    fun refresh() = launch {
        log(tag) { "refresh()" }
        accessRepo.refresh("manual")
    }

    fun resetIdentity() = launch {
        log(tag) { "resetIdentity()" }
        sessionManager.resetIdentity()
    }
}
