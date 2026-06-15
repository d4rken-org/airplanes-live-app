package eu.darken.apl.account.core

/** No (valid) session is available; the user must run the full auth flow. */
class NotLoggedInException : IllegalStateException("Not logged in")

/** The user dismissed the login flow (closed the Custom Tab / pressed back). Not a real error. */
class LoginCancelledException : Exception("Login cancelled by user")

/**
 * The refresh token family was revoked (e.g. `invalid_grant` / reuse detection). The stored
 * session has been cleared; the user must re-authenticate.
 */
class SessionExpiredException : IllegalStateException("Session expired, re-authentication required")

/**
 * Rate limited on an OAuth (`/o/`) endpoint. The session is still valid — back off and retry.
 * [retryAfterSeconds] may be null if the server didn't expose it.
 */
class OAuthRateLimitedException(val retryAfterSeconds: Int?) :
    IllegalStateException("OAuth rate limited" + (retryAfterSeconds?.let { ", retry after ${it}s" } ?: ""))
