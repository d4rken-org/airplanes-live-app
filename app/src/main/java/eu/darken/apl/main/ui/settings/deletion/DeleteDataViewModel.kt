package eu.darken.apl.main.ui.settings.deletion

import android.content.ActivityNotFoundException
import android.content.Context
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.apl.R
import eu.darken.apl.common.EmailTool
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.Logging.Priority.INFO
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.uix.ViewModel4
import eu.darken.apl.server.deletion.DeletionResult
import eu.darken.apl.server.deletion.DeletionState
import eu.darken.apl.server.deletion.InstallationDeletion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class DeleteDataViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    @param:ApplicationContext private val context: Context,
    private val deletion: InstallationDeletion,
    private val emailTool: EmailTool,
) : ViewModel4(
    dispatcherProvider = dispatcherProvider,
    tag = logTag("Settings", "DeleteData", "VM"),
) {

    sealed interface State {
        data object Idle : State
        data object Running : State

        /** A deletion from an earlier visit or process whose outcome is unknown. */
        data object Unfinished : State
        data class NotConfirmed(val tryLater: Boolean) : State
        data class Refused(val installationId: String?) : State
        data object UpdateRequired : State
        data object WipeFailed : State
    }

    private val isRunning = MutableStateFlow(false)
    private val lastResult = MutableStateFlow<DeletionResult?>(null)

    /** Null until the stored deletion state is known. */
    val state: StateFlow<State?> = combine(isRunning, lastResult, deletion.state) { running, result, persisted ->
        when {
            running -> State.Running
            result != null -> result.toState()
            persisted == DeletionState.REQUEST_PENDING -> State.Unfinished
            // The server already confirmed it, init finishes the deletion without asking
            persisted == DeletionState.WIPE_PENDING -> State.Running
            else -> State.Idle
        }
    }.stateIn(vmScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        launch {
            if (deletion.state.first() == DeletionState.WIPE_PENDING) {
                log(tag, INFO) { "The server already confirmed a deletion, finishing it" }
                delete()
            }
        }
    }

    fun delete() {
        if (!isRunning.compareAndSet(expect = false, update = true)) {
            log(tag, WARN) { "delete(): already running" }
            return
        }
        launch {
            try {
                val result = deletion.delete()
                log(tag, INFO) { "delete(): $result" }
                lastResult.value = result
            } finally {
                isRunning.value = false
            }
        }
    }

    fun cancelPending() = launch {
        log(tag) { "cancelPending()" }
        deletion.cancelPending()
    }

    fun sendEmail() {
        val installationId = (lastResult.value as? DeletionResult.Refused)?.installationId
        log(tag) { "sendEmail(installationId=$installationId)" }
        val body = buildString {
            append(context.getString(R.string.settings_delete_data_email_body))
            if (installationId != null) {
                append("\n\n")
                append(context.getString(R.string.settings_delete_data_email_id_line, installationId))
            }
        }
        val intent = emailTool.build(
            EmailTool.Email(
                receipients = listOf(SUPPORT_EMAIL),
                subject = context.getString(R.string.settings_delete_data_email_subject),
                body = body,
            )
        )
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // The screen shows the address, it can be written to by hand
            log(tag, WARN) { "sendEmail(): no email app: $e" }
        }
    }

    private fun DeletionResult.toState(): State = when (this) {
        // The device data is being cleared, the process ends any moment
        DeletionResult.Deleted -> State.Running
        is DeletionResult.WipeFailed -> State.WipeFailed
        is DeletionResult.Refused -> State.Refused(installationId)
        DeletionResult.UpdateRequired -> State.UpdateRequired
        is DeletionResult.NotConfirmed -> State.NotConfirmed(tryLater = retryAfterSeconds != null)
    }

    companion object {
        const val SUPPORT_EMAIL = "support@darken.eu"
    }
}
