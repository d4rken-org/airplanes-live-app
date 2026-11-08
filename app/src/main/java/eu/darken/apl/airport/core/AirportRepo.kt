package eu.darken.apl.airport.core

import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.server.api.AirportSearchRequest
import eu.darken.apl.server.api.ServerApiException
import eu.darken.apl.server.api.ServerCodes
import eu.darken.apl.server.api.ServerEndpoint
import eu.darken.apl.server.api.retryAfter
import eu.darken.apl.server.compat.AppCompatibility
import eu.darken.apl.server.compat.CompatState
import eu.darken.apl.server.session.SessionManager
import eu.darken.apl.server.session.SessionRevokedException
import eu.darken.apl.server.session.SessionState
import kotlinx.coroutines.CancellationException
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AirportRepo @Inject constructor(
    private val endpoint: ServerEndpoint,
    private val sessionManager: SessionManager,
    private val compatibility: AppCompatibility,
    private val clock: MonotonicClock,
) {

    /** Only ever grows: a shorter Retry-After never shortens the cooldown, and a success never ends it. */
    private val cooldownUntil = AtomicLong(Long.MIN_VALUE)

    /** [terms] are sent as they are, at most 10. */
    suspend fun search(terms: List<String>): AirportSearch = try {
        compatibility.awaitLoaded()
        if (compatibility.state.value is CompatState.UpdateRequired) {
            AirportSearch.UpdateRequired
        } else if (sessionManager.state.value is SessionState.Revoked) {
            AirportSearch.Revoked
        } else if (isCoolingDown()) {
            AirportSearch.RateLimited
        } else {
            // Checked again in the block: another search's 429 can land while the credentials resolve
            sessionManager.authed { token ->
                if (isCoolingDown()) {
                    AirportSearch.RateLimited
                } else {
                    AirportSearch.Answered(endpoint.searchAirports(token, AirportSearchRequest(terms)).results)
                }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: SessionRevokedException) {
        AirportSearch.Revoked
    } catch (e: ServerApiException) {
        log(TAG, WARN) { "search(${terms.size} terms) failed: ${e.code} (HTTP ${e.status})" }
        when {
            e.status == 404 -> AirportSearch.Unavailable
            e.status == 429 -> {
                startCooldown(e)
                AirportSearch.RateLimited
            }

            e.code == ServerCodes.APP_UPDATE_REQUIRED -> AirportSearch.UpdateRequired
            e.code == ServerCodes.INSTALLATION_RESTRICTED -> AirportSearch.Restricted
            e.code == ServerCodes.INSTALLATION_REVOKED -> AirportSearch.Revoked
            else -> AirportSearch.Failed(e)
        }
    } catch (e: Exception) {
        log(TAG, WARN) { "search(${terms.size} terms) failed: ${e.asLog()}" }
        AirportSearch.Failed(e)
    }

    private fun isCoolingDown(): Boolean = cooldownUntil.get() > clock.elapsed()

    private fun startCooldown(e: ServerApiException) {
        val until = clock.elapsed() + e.retryAfter.toMillis().coerceAtLeast(0)
        cooldownUntil.accumulateAndGet(until, ::maxOf)
    }

    companion object {
        private val TAG = logTag("Airport", "Repo")
    }
}
