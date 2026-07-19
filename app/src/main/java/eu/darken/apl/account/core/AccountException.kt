package eu.darken.apl.account.core

import android.content.Context
import eu.darken.apl.R
import eu.darken.apl.common.ca.caString
import eu.darken.apl.common.error.HasLocalizedError
import eu.darken.apl.common.error.LocalizedError

/** No (valid) session is available; the user must run the full auth flow. */
class NotLoggedInException : IllegalStateException("Not logged in"), HasLocalizedError {
    override fun getLocalizedError(context: Context): LocalizedError = LocalizedError(
        throwable = this,
        label = caString { getString(R.string.account_error_not_logged_in_title) },
        description = caString { getString(R.string.account_error_not_logged_in_message) },
    )
}

/** The user dismissed the login flow (closed the Custom Tab / pressed back). Not a real error. */
class LoginCancelledException : Exception("Login cancelled by user")

/**
 * The refresh token family was revoked (e.g. `invalid_grant` / reuse detection). The stored
 * session has been cleared; the user must re-authenticate.
 */
class SessionExpiredException :
    IllegalStateException("Session expired, re-authentication required"), HasLocalizedError {
    override fun getLocalizedError(context: Context): LocalizedError = LocalizedError(
        throwable = this,
        label = caString { getString(R.string.account_error_session_expired_title) },
        description = caString { getString(R.string.account_error_session_expired_message) },
    )
}

/**
 * Rate limited on an OAuth (`/o/`) endpoint. The session is still valid — back off and retry.
 * The server sends a retry-after hint, but AppAuth's exception surface can't carry non-standard
 * response fields, so no delay is exposed here.
 */
class OAuthRateLimitedException : IllegalStateException("OAuth rate limited"), HasLocalizedError {
    override fun getLocalizedError(context: Context): LocalizedError = LocalizedError(
        throwable = this,
        label = caString { getString(R.string.account_error_rate_limited_title) },
        description = caString { getString(R.string.account_error_rate_limited_message) },
    )
}
