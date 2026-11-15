package eu.darken.apl.airport.core

import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.main.core.aircraft.toAircraft
import eu.darken.apl.main.core.request.Bucket
import eu.darken.apl.main.core.request.OperationStore
import eu.darken.apl.main.core.request.RequestCoordinator
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.api.AirportFlightsPageDto
import eu.darken.apl.server.api.AirportFlightsPassDto
import eu.darken.apl.server.api.AirportFlightsRequest
import eu.darken.apl.server.api.AirportFlightsResponse
import eu.darken.apl.server.api.ServerApiException
import eu.darken.apl.server.api.ServerCodes
import eu.darken.apl.server.api.ServerEndpoint
import eu.darken.apl.server.api.isRetryableSameRequest
import eu.darken.apl.server.api.retryAfter
import eu.darken.apl.server.identity.newOperationId
import eu.darken.apl.server.session.SessionManager
import eu.darken.apl.server.session.SessionRevokedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * Sends airport flights operations. The caller holds the [AirportFlightsOperation] and decides when to make a
 * new one; nothing here remembers a pending operation.
 */
@Singleton
class AirportFlightsRepo @Inject constructor(
    private val endpoint: ServerEndpoint,
    private val sessionManager: SessionManager,
    private val requestCoordinator: RequestCoordinator,
    private val accessRepo: AccessRepo,
    private val aircraftRepo: AircraftRepo,
    private val clock: MonotonicClock,
) {

    private val _pass = MutableStateFlow<AirportFlightsPass?>(null)
    val pass: StateFlow<AirportFlightsPass?> = _pass.asStateFlow()

    /** Airport id to the monotonic time its `refresh_too_soon` cooldown ends. */
    private val cooldowns = MutableStateFlow<Map<String, Long>>(emptyMap())

    fun newLoad(airportId: String): AirportFlightsOperation = AirportFlightsOperation(
        kind = AirportFlightsOperation.Kind.Load(airportId),
        request = AirportFlightsRequest(operationId = newOperationId(), airportId = airportId),
    )

    fun newPage(evaluationId: String, direction: String, cursor: String): AirportFlightsOperation =
        AirportFlightsOperation(
            kind = AirportFlightsOperation.Kind.Page(evaluationId, direction, cursor),
            request = AirportFlightsRequest(
                operationId = newOperationId(),
                page = AirportFlightsPageDto(evaluationId = evaluationId, direction = direction, cursor = cursor),
            ),
        )

    /** Monotonic, null when [airportId] may be asked for now. */
    fun cooldownUntil(airportId: String): Long? = cooldowns.value[airportId]?.takeIf { it > clock.elapsed() }

    /**
     * Retries [operation] itself while that can still be answered from the replay store. [onWaiting] is called
     * with the monotonic end of a wait the server asked for, before it starts.
     */
    suspend fun send(
        operation: AirportFlightsOperation,
        onWaiting: (untilElapsed: Long) -> Unit = {},
    ): AirportFlightsResult {
        val kind = operation.kind
        if (kind is AirportFlightsOperation.Kind.Load) {
            cooldownUntil(kind.airportId)?.let { return AirportFlightsResult.CoolingDown(it) }
        }
        // An operation that never went out has no replay deadline, its retries still end after the same window
        val unsentBound = clock.elapsed() + OperationStore.REPLAY_WINDOW.toMillis()
        var backoffMillis = INITIAL_BACKOFF_MS

        while (true) {
            // A send cancelled during this wait leaves it on the operation, so the next send keeps it
            operation.notBeforeElapsed?.let { notBefore ->
                val now = clock.elapsed()
                if (notBefore > now) {
                    val deadline = operation.deadline
                    if (deadline != null && notBefore >= deadline) {
                        log(TAG, WARN) { "$operation unavailable until $notBefore, past its deadline $deadline" }
                        return AirportFlightsResult.FlightsUnavailable(notBefore)
                    }
                    log(TAG) { "$operation unavailable, retrying at $notBefore" }
                    onWaiting(notBefore)
                    delay(notBefore - now)
                }
            }

            val error: Exception = try {
                val response = attempt(operation)
                if (response == null) {
                    log(TAG) { "$operation passed its replay deadline, not sent" }
                    return AirportFlightsResult.Expired
                }
                return apply(response)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e
            }

            when {
                error is IOException || (error is ServerApiException && error.isRetryableSameRequest) -> {
                    log(TAG, WARN) { "$operation failed, retrying: ${error.asLog()}" }
                    val now = clock.elapsed()
                    val bound = operation.deadline ?: unsentBound
                    if (now >= bound) return AirportFlightsResult.Expired
                    delay(min(backoffMillis, bound - now))
                    backoffMillis = min(backoffMillis * 2, MAX_BACKOFF_MS)
                }

                error is ServerApiException && error.code == ServerCodes.FLIGHTS_UNAVAILABLE -> {
                    // Waited for, or returned past the deadline, at the top of the loop
                    operation.notBeforeElapsed = clock.elapsed() + error.retryAfter.toMillis().coerceAtLeast(0)
                }

                else -> return classify(operation, error)
            }
        }
    }

    /** Null when the operation's deadline had passed by the time it could go out. */
    private suspend fun attempt(operation: AirportFlightsOperation): AirportFlightsResponse? =
        requestCoordinator.execute(Bucket.SEARCH) {
            sessionManager.authed { token ->
                // Also checked when authed runs this again after a token refresh, or after waiting for admission
                if (operation.claimSend(clock.elapsed())) {
                    log(TAG, VERBOSE) { "Sending $operation" }
                    endpoint.airportFlights(token, operation.request)
                } else {
                    null
                }
            }
        }

    private fun classify(operation: AirportFlightsOperation, error: Exception): AirportFlightsResult {
        if (error is SessionRevokedException) return AirportFlightsResult.Revoked
        if (error !is ServerApiException) {
            log(TAG, WARN) { "$operation failed: ${error.asLog()}" }
            return AirportFlightsResult.Failed(error)
        }
        log(TAG, WARN) { "$operation failed: ${error.code} (HTTP ${error.status})" }
        return when {
            error.code == ServerCodes.REFRESH_TOO_SOON -> {
                val until = clock.elapsed() + error.retryAfter.toMillis().coerceAtLeast(0)
                (operation.kind as? AirportFlightsOperation.Kind.Load)?.let { startCooldown(it.airportId, until) }
                AirportFlightsResult.CoolingDown(cooldownUntil(operation.kind) ?: until)
            }

            error.code == ServerCodes.UNKNOWN_AIRPORT -> AirportFlightsResult.UnknownAirport
            error.code == ServerCodes.APP_UPDATE_REQUIRED -> AirportFlightsResult.UpdateRequired
            error.code == ServerCodes.INSTALLATION_RESTRICTED -> AirportFlightsResult.Restricted
            error.code == ServerCodes.INSTALLATION_REVOKED -> AirportFlightsResult.Revoked
            error.status == 404 -> AirportFlightsResult.NotOffered
            else -> AirportFlightsResult.Failed(error)
        }
    }

    private fun cooldownUntil(kind: AirportFlightsOperation.Kind): Long? =
        (kind as? AirportFlightsOperation.Kind.Load)?.let { cooldownUntil(it.airportId) }

    /** Only ever grows: a shorter Retry-After never shortens a cooldown, and a success never ends it. */
    private fun startCooldown(airportId: String, until: Long) {
        val now = clock.elapsed()
        cooldowns.update { current ->
            val kept = current.filterValues { it > now }
            kept + (airportId to maxOf(kept[airportId] ?: Long.MIN_VALUE, until))
        }
    }

    private suspend fun apply(response: AirportFlightsResponse): AirportFlightsResult {
        applyPass(response.pass)
        accessRepo.applyUsage(response.usage)
        return when (response.outcome) {
            AirportFlightsResponse.OUTCOME_ANSWERED -> {
                writeAircraft(response)
                AirportFlightsResult.Answered(response)
            }

            AirportFlightsResponse.OUTCOME_ALLOWANCE_EXHAUSTED -> AirportFlightsResult.AllowanceExhausted

            AirportFlightsResponse.OUTCOME_PASS_REQUIRED,
            AirportFlightsResponse.OUTCOME_EVALUATION_EXPIRED,
            AirportFlightsResponse.OUTCOME_RESULT_EXPIRED,
            AirportFlightsResponse.OUTCOME_ACCESS_CHANGED -> AirportFlightsResult.ReloadNeeded(response.outcome)

            else -> {
                log(TAG, WARN) { "Unknown outcome ${response.outcome} for ${response.operationId}" }
                AirportFlightsResult.UnknownOutcome(response.outcome)
            }
        }
    }

    /**
     * Within one scope a lower revision is an older answer that arrived late. A new scope means the allowance
     * owner changed, its revisions do not compare with the old ones.
     */
    private fun applyPass(dto: AirportFlightsPassDto) {
        val received = AirportFlightsPass(
            scope = dto.scope,
            revision = dto.revision,
            airportId = dto.airportId,
            validUntil = dto.validUntil.takeIf { dto.airportId != null },
        )
        _pass.update { held ->
            when {
                held == null || held.scope != received.scope || received.revision >= held.revision -> received
                else -> {
                    log(TAG, VERBOSE) { "Ignoring pass revision ${received.revision}, holding ${held.revision}" }
                    held
                }
            }
        }
    }

    /**
     * Dated by the evaluation, not by arrival: a page or a replay carries observations from when the list was
     * evaluated.
     */
    private suspend fun writeAircraft(response: AirportFlightsResponse) {
        if (response.aircraft.isEmpty()) return
        val evaluatedAt = response.evaluatedAt
        if (evaluatedAt == null) {
            log(TAG, WARN) { "Answer ${response.operationId} without evaluatedAt, not caching its aircraft" }
            return
        }
        val fetchedAt = Instant.ofEpochMilli(evaluatedAt)
        aircraftRepo.update(response.aircraft.map { it.toAircraft(fetchedAt) })
    }

    companion object {
        private const val INITIAL_BACKOFF_MS = 1_000L
        private const val MAX_BACKOFF_MS = 30_000L
        private val TAG = logTag("Airport", "Flights", "Repo")
    }
}
