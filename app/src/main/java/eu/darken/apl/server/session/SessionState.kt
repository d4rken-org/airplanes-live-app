package eu.darken.apl.server.session

import android.content.Context
import eu.darken.apl.R
import eu.darken.apl.common.ca.toCaString
import eu.darken.apl.common.error.HasLocalizedError
import eu.darken.apl.common.error.LocalizedError

sealed interface SessionState {
    /** The stored session has not been read yet. */
    data object Unknown : SessionState

    data object NoSession : SessionState

    data class Active(val installationId: String) : SessionState

    /** Terminal until the user resets the identity, the server refuses this installation. */
    data object Revoked : SessionState

    /** The device key vanished, the app enrolls again as a new installation. */
    data object KeyLost : SessionState
}

class SessionRevokedException : RuntimeException("This installation was revoked by the server"), HasLocalizedError {
    override fun getLocalizedError(context: Context) = LocalizedError(
        throwable = this,
        label = R.string.server_error_title.toCaString(),
        description = R.string.server_error_revoked_msg.toCaString(),
    )
}
