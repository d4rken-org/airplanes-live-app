package eu.darken.apl.airport.ui.flights

import eu.darken.apl.airport.core.AirportFlightList
import eu.darken.apl.airport.core.AirportFlightsOperation
import eu.darken.apl.airport.core.AirportFlightsRepo
import eu.darken.apl.airport.core.AirportFlightsResult
import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.api.ServerApiException
import eu.darken.apl.server.api.ServerCodes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.min

/**
 * Loads, refreshes and pages one airport's flight list for one sheet. Nothing is sent while the sheet is not
 * visible. [isPro] is null until the upgrade state is known. [close] ends it for good.
 *
 * State is guarded by one lock because intents come from the UI thread while results arrive on the owner's
 * dispatcher. Jobs are started lazily after being stored, so an undispatched start still finds itself current.
 */
class AirportFlightsController(
    scope: CoroutineScope,
    private val airportId: String,
    private val airportTimeZone: String?,
    private val repo: AirportFlightsRepo,
    isPro: Flow<Boolean?>,
    private val serverClock: ServerClock,
    private val monotonicClock: MonotonicClock,
    private val deviceZone: () -> ZoneId,
) {

    private val job = SupervisorJob(scope.coroutineContext[Job])
    private val scope = scope + job
    private val lock = Any()

    private val _state = MutableStateFlow<AirportFlightsUiState>(AirportFlightsUiState.Resolving)
    val state: StateFlow<AirportFlightsUiState> = _state.asStateFlow()

    private sealed interface Problem {
        data class Failed(val error: Throwable, val canRetry: Boolean) : Problem
        data class PageFailed(val error: Throwable) : Problem
        data object ReloadOffered : Problem
        data object AllowanceExhausted : Problem
        data class Waiting(val untilElapsed: Long) : Problem
    }

    private var pro: Boolean? = null
    private var visible = false
    private var closed = false
    private var terminal: AirportFlightsUiState? = null

    private var list: AirportFlightList? = null
    private var problem: Problem? = null

    /** Sent or still to be sent; after a failure it stays for [retry] while [loading] is false. */
    private var pendingLoad: AirportFlightsOperation? = null
    private var loading = false
    private var loadJob: Job? = null

    /** By wire direction. */
    private val pendingPages = mutableMapOf<String, AirportFlightsOperation>()
    private val pageJobs = mutableMapOf<String, Job>()

    /** Monotonic, when Pro's next load is due. */
    private var nextLoadAt: Long? = null
    private var timerJob: Job? = null
    private var proStopped = false
    private var autoReloadUsed = false
    private var refreshIntervalMs = DEFAULT_REFRESH_MS
    private var backoffMs: Long? = null

    /** Monotonic, end of this airport's refresh cooldown. */
    private var refreshBlockedUntil: Long? = null
    private var unblockJob: Job? = null

    /** Monotonic, the server's Retry-After for flight data: no load goes out before it. */
    private var unavailableUntil: Long? = null
    private var unavailableJob: Job? = null
    private var tickJob: Job? = null

    init {
        this.scope.launch {
            isPro.filterNotNull().distinctUntilChanged().collect { onPro(it) }
        }
        this.scope.launch {
            repo.pass.collect { synchronized(lock) { render() } }
        }
    }

    fun showFlights() {
        synchronized(lock) {
            settleUnavailableForIntent()
            if (!canLoad()) return
            log(TAG) { "showFlights()" }
            startNewLoad()
            render()
        }
    }

    fun refresh() {
        synchronized(lock) {
            settleUnavailableForIntent()
            if (!canLoad() || list == null || isRefreshBlocked()) return
            log(TAG) { "refresh()" }
            startNewLoad()
            render()
        }
    }

    fun reload() {
        synchronized(lock) {
            settleUnavailableForIntent()
            if (!canLoad()) return
            log(TAG) { "reload()" }
            startNewLoad()
            render()
        }
    }

    fun retry() {
        synchronized(lock) {
            settleUnavailableForIntent()
            if (!canLoad()) return
            val failed = problem as? Problem.Failed ?: return
            if (!failed.canRetry) return
            log(TAG) { "retry()" }
            loadAgain()
            render()
        }
    }

    /** [direction] is the wire direction whose next page to load. */
    fun showMore(direction: String) {
        synchronized(lock) {
            if (closed || pro == null || terminal != null) return
            val current = list ?: return
            val cursor = current.direction(direction)?.next ?: return
            pendingPages[direction]?.let { op ->
                val kind = op.kind as AirportFlightsOperation.Kind.Page
                if (kind.evaluationId == current.evaluationId && kind.cursor == cursor) return
            }
            log(TAG) { "showMore($direction)" }
            if (problem is Problem.PageFailed) problem = null
            pageJobs.remove(direction)?.cancel()
            val op = repo.newPage(current.evaluationId, direction, cursor)
            pendingPages[direction] = op
            if (visible) launchPage(direction, op)
            render()
        }
    }

    fun setVisible(visible: Boolean) {
        synchronized(lock) {
            if (closed || this.visible == visible) return
            log(TAG) { "setVisible($visible)" }
            this.visible = visible
            if (visible) {
                startTicks()
                resume()
            } else {
                loadJob?.cancel()
                loadJob = null
                pageJobs.values.forEach { it.cancel() }
                pageJobs.clear()
                cancelTimer()
                unblockJob?.cancel()
                unblockJob = null
                unavailableJob?.cancel()
                unavailableJob = null
                tickJob?.cancel()
                tickJob = null
            }
            render()
        }
    }

    fun close() {
        synchronized(lock) {
            if (closed) return
            log(TAG) { "close()" }
            closed = true
            job.cancel()
            loadJob = null
            pageJobs.clear()
            timerJob = null
            unblockJob = null
            unavailableJob = null
            tickJob = null
        }
    }

    private fun onPro(isPro: Boolean) {
        synchronized(lock) {
            if (closed) return
            log(TAG) { "onPro($isPro)" }
            pro = isPro
            if (isPro) {
                if (!loading && nextLoadAt == null && !proStopped) {
                    val now = monotonicClock.elapsed()
                    nextLoadAt = unavailableUntil?.takeIf { it > now } ?: now
                }
            } else {
                cancelTimer()
                nextLoadAt = null
            }
            resume()
            render()
        }
    }

    private fun canLoad(): Boolean = !closed && pro != null && terminal == null && !loading && !isUnavailable()

    private fun isUnavailable(): Boolean = unavailableUntil?.let { it > monotonicClock.elapsed() } ?: false

    /**
     * Ends a passed [unavailableUntil]. Free's waiting notice for it turns into an offer to retry, which then
     * makes a new load.
     */
    private fun settleUnavailable(): Boolean {
        val until = unavailableUntil ?: return false
        if (until > monotonicClock.elapsed()) return false
        unavailableUntil = null
        unavailableJob?.cancel()
        unavailableJob = null
        val shown = problem
        if (pro != true && shown is Problem.Waiting && shown.untilElapsed == until) {
            problem = Problem.Failed(AirportFlightsUnavailableException("Flights unavailable"), canRetry = true)
        }
        return true
    }

    /** An intent can arrive before the timer that would have settled a passed wait. */
    private fun settleUnavailableForIntent() {
        if (settleUnavailable()) render()
    }

    private fun isRefreshBlocked(): Boolean = refreshBlockedUntil?.let { it > monotonicClock.elapsed() } ?: false

    /** Resends a failed load while it can still be replayed, else makes a new one. */
    private fun loadAgain() {
        val op = pendingLoad
        if (op != null && !op.isExpired(monotonicClock.elapsed())) sendLoad(op) else startNewLoad()
    }

    private fun startNewLoad() = sendLoad(repo.newLoad(airportId))

    private fun sendLoad(op: AirportFlightsOperation) {
        cancelTimer()
        nextLoadAt = null
        pendingLoad = op
        loading = true
        problem = null
        if (visible) launchLoad(op)
    }

    /** Picks up what hiding interrupted, sending at most one load. */
    private fun resume() {
        if (!visible || closed || terminal != null || pro == null) return
        settleUnavailable()
        val now = monotonicClock.elapsed()

        val op = pendingLoad
        if (op != null && loading) {
            if (!op.isExpired(now)) {
                if (loadJob?.isActive != true) launchLoad(op)
            } else {
                log(TAG) { "$op passed its replay window while hidden" }
                pendingLoad = null
                loading = false
                when {
                    pro == true -> nextLoadAt = now
                    list != null -> problem = Problem.ReloadOffered
                }
            }
        }

        pendingPages.entries.toList().forEach { (direction, page) ->
            val kind = page.kind as AirportFlightsOperation.Kind.Page
            when {
                page.isExpired(now) || list?.evaluationId != kind.evaluationId -> pendingPages.remove(direction)
                pageJobs[direction]?.isActive != true -> launchPage(direction, page)
            }
        }

        scheduleTimer()
        scheduleUnblock()
        scheduleUnavailableEnd()
    }

    private fun launchLoad(op: AirportFlightsOperation) {
        loadJob?.cancel()
        val started = scope.launch(start = CoroutineStart.LAZY) {
            val self = coroutineContext.job
            val result = sendSafely(op) { until ->
                synchronized(lock) {
                    if (loadJob !== self) return@synchronized
                    problem = Problem.Waiting(until)
                    render()
                }
            }
            synchronized(lock) {
                if (loadJob !== self) return@launch
                loadJob = null
                onLoadResult(op, result)
                render()
            }
        }
        loadJob = started
        started.start()
    }

    private fun launchPage(direction: String, op: AirportFlightsOperation) {
        val started = scope.launch(start = CoroutineStart.LAZY) {
            val self = coroutineContext.job
            val result = sendSafely(op) {}
            synchronized(lock) {
                if (pageJobs[direction] !== self) return@launch
                pageJobs.remove(direction)
                if (pendingPages[direction] === op) pendingPages.remove(direction)
                onPageResult(direction, op, result)
                render()
            }
        }
        pageJobs[direction] = started
        started.start()
    }

    private suspend fun sendSafely(
        op: AirportFlightsOperation,
        onWaiting: (Long) -> Unit,
    ): AirportFlightsResult = try {
        repo.send(op, onWaiting)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log(TAG, WARN) { "Sending $op failed: ${e.asLog()}" }
        AirportFlightsResult.Failed(e)
    }

    private fun onLoadResult(op: AirportFlightsOperation, result: AirportFlightsResult) {
        log(TAG) { "Load ${op.operationId}: $result" }
        pendingLoad = null
        loading = false
        val now = monotonicClock.elapsed()

        when (result) {
            is AirportFlightsResult.Answered -> {
                val answered = AirportFlightList.from(result.response)
                if (answered == null) {
                    unknownOutcome()
                    return
                }
                list = answered
                problem = null
                clearPages()
                autoReloadUsed = false
                backoffMs = null
                proStopped = false
                val interval = result.response.refreshAfter
                    ?.let { (it - serverClock.now().toEpochMilli()).coerceAtLeast(0) }
                    ?: DEFAULT_REFRESH_MS
                refreshIntervalMs = interval
                scheduleProLoad(now + interval)
            }

            AirportFlightsResult.AllowanceExhausted -> allowanceExhausted()

            is AirportFlightsResult.ReloadNeeded, AirportFlightsResult.Expired -> offerReload()

            is AirportFlightsResult.FlightsUnavailable -> {
                unavailableUntil = result.untilElapsed
                problem = Problem.Waiting(result.untilElapsed)
                scheduleUnavailableEnd()
                if (pro == true) scheduleProLoad(result.untilElapsed)
            }

            is AirportFlightsResult.CoolingDown -> {
                refreshBlockedUntil = result.untilElapsed
                scheduleUnblock()
                if (pro == true) {
                    if (list == null) problem = Problem.Waiting(result.untilElapsed)
                    scheduleProLoad(result.untilElapsed)
                } else if (list == null) {
                    problem = Problem.Failed(ServerApiException(ServerCodes.REFRESH_TOO_SOON, 429), canRetry = true)
                }
            }

            is AirportFlightsResult.Failed -> {
                pendingLoad = op
                problem = Problem.Failed(result.error, canRetry = true)
                if (pro == true) {
                    val wait = backoffMs?.let { min(it * 2, MAX_BACKOFF_MS) }
                        ?: min(refreshIntervalMs.takeIf { it > 0 } ?: DEFAULT_REFRESH_MS, MAX_BACKOFF_MS)
                    backoffMs = wait
                    scheduleProLoad(now + wait)
                }
            }

            is AirportFlightsResult.UnknownOutcome -> unknownOutcome()

            AirportFlightsResult.NotOffered -> terminate(AirportFlightsUiState.NotOffered)
            AirportFlightsResult.UnknownAirport -> terminate(AirportFlightsUiState.UnknownAirport)
            AirportFlightsResult.UpdateRequired -> terminate(AirportFlightsUiState.UpdateRequired)
            AirportFlightsResult.Restricted -> terminate(AirportFlightsUiState.Restricted)
            AirportFlightsResult.Revoked -> terminate(AirportFlightsUiState.Revoked)
        }
    }

    private fun onPageResult(direction: String, op: AirportFlightsOperation, result: AirportFlightsResult) {
        log(TAG) { "Page ${op.operationId} ($direction): $result" }
        val kind = op.kind as AirportFlightsOperation.Kind.Page
        val current = list
        if (current == null || current.evaluationId != kind.evaluationId) {
            log(TAG) { "Discarding page of replaced evaluation ${kind.evaluationId}" }
            return
        }

        when (result) {
            is AirportFlightsResult.Answered -> {
                val merged = current.appendPage(result.response, direction)
                if (merged == null) {
                    log(TAG, WARN) { "Discarding page answer for evaluation ${result.response.evaluationId}" }
                    return
                }
                list = merged
            }

            // A running load replaces the list anyway
            is AirportFlightsResult.ReloadNeeded, AirportFlightsResult.Expired -> if (!loading) offerReload()

            AirportFlightsResult.AllowanceExhausted -> allowanceExhausted()

            AirportFlightsResult.NotOffered -> terminate(AirportFlightsUiState.NotOffered)
            AirportFlightsResult.UnknownAirport -> terminate(AirportFlightsUiState.UnknownAirport)
            AirportFlightsResult.UpdateRequired -> terminate(AirportFlightsUiState.UpdateRequired)
            AirportFlightsResult.Restricted -> terminate(AirportFlightsUiState.Restricted)
            AirportFlightsResult.Revoked -> terminate(AirportFlightsUiState.Revoked)

            is AirportFlightsResult.Failed -> problem = Problem.PageFailed(result.error)

            is AirportFlightsResult.UnknownOutcome,
            is AirportFlightsResult.FlightsUnavailable,
            is AirportFlightsResult.CoolingDown -> {
                problem = Problem.PageFailed(AirportFlightsUnavailableException("Page not answered: $result"))
            }
        }
    }

    /** Pro reloads once on its own; a second time in a row, or Free, it is up to the user. */
    private fun offerReload() {
        if (pro == true && !autoReloadUsed && !proStopped) {
            log(TAG) { "Reloading once on its own" }
            autoReloadUsed = true
            startNewLoad()
        } else {
            problem = Problem.ReloadOffered
            cancelTimer()
            nextLoadAt = null
        }
    }

    private fun allowanceExhausted() {
        problem = Problem.AllowanceExhausted
        stopPro()
    }

    private fun unknownOutcome() {
        problem = Problem.Failed(AirportFlightsUnavailableException("Unknown outcome"), canRetry = false)
        stopPro()
    }

    private fun stopPro() {
        proStopped = true
        cancelTimer()
        nextLoadAt = null
    }

    private fun terminate(state: AirportFlightsUiState) {
        log(TAG) { "Stopping at $state" }
        terminal = state
        problem = null
        pendingLoad = null
        loading = false
        loadJob?.cancel()
        loadJob = null
        clearPages()
        cancelTimer()
        nextLoadAt = null
        unblockJob?.cancel()
        unblockJob = null
        unavailableJob?.cancel()
        unavailableJob = null
    }

    private fun clearPages() {
        pageJobs.values.forEach { it.cancel() }
        pageJobs.clear()
        pendingPages.clear()
    }

    private fun scheduleProLoad(at: Long) {
        if (pro != true || proStopped) return
        nextLoadAt = at
        scheduleTimer()
    }

    private fun cancelTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    private fun scheduleTimer() {
        cancelTimer()
        val at = nextLoadAt ?: return
        if (!visible || closed || terminal != null || pro != true || proStopped || loading) return
        val started = scope.launch(start = CoroutineStart.LAZY) {
            val self = coroutineContext.job
            delay((at - monotonicClock.elapsed()).coerceAtLeast(0))
            synchronized(lock) {
                if (timerJob !== self) return@launch
                timerJob = null
                nextLoadAt = null
                loadAgain()
                render()
            }
        }
        timerJob = started
        started.start()
    }

    private fun scheduleUnblock() {
        unblockJob?.cancel()
        unblockJob = null
        val until = refreshBlockedUntil ?: return
        if (!visible || closed) return
        val wait = until - monotonicClock.elapsed()
        if (wait <= 0) {
            refreshBlockedUntil = null
            return
        }
        val started = scope.launch(start = CoroutineStart.LAZY) {
            val self = coroutineContext.job
            delay(wait)
            synchronized(lock) {
                if (unblockJob !== self) return@launch
                unblockJob = null
                render()
            }
        }
        unblockJob = started
        started.start()
    }

    private fun scheduleUnavailableEnd() {
        unavailableJob?.cancel()
        unavailableJob = null
        val until = unavailableUntil ?: return
        if (!visible || closed) return
        val wait = until - monotonicClock.elapsed()
        if (wait <= 0) {
            settleUnavailable()
            return
        }
        val started = scope.launch(start = CoroutineStart.LAZY) {
            val self = coroutineContext.job
            delay(wait)
            synchronized(lock) {
                if (unavailableJob !== self) return@launch
                unavailableJob = null
                settleUnavailable()
                render()
            }
        }
        unavailableJob = started
        started.start()
    }

    private fun startTicks() {
        tickJob?.cancel()
        val started = scope.launch(start = CoroutineStart.LAZY) {
            val self = coroutineContext.job
            while (true) {
                val wait = synchronized(lock) {
                    if (tickJob !== self) return@launch
                    millisToNextTick()
                }
                delay(wait)
                synchronized(lock) {
                    if (tickJob !== self) return@launch
                    render()
                }
            }
        }
        tickJob = started
        started.start()
    }

    /** The next minute, or sooner when the list goes stale, the pass ends or the date changes. */
    private fun millisToNextTick(): Long {
        val now = serverClock.now()
        val zone = displayZone().zone
        val next = listOfNotNull(
            now.plusMillis(TICK_MS),
            list?.expiresAt,
            repo.pass.value?.takeIf { it.airportId == airportId }?.validUntil?.let { Instant.ofEpochMilli(it) },
            now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant(),
        ).filter { it.isAfter(now) }.min()
        return Duration.between(now, next).toMillis().coerceAtLeast(1)
    }

    private fun displayZone(): DisplayZone =
        DisplayZone.resolve(listOf(list?.airport?.timeZone, airportTimeZone), deviceZone())

    private fun render() {
        _state.value = buildState()
    }

    private fun buildState(): AirportFlightsUiState {
        if (pro == null) return AirportFlightsUiState.Resolving
        terminal?.let { return it }

        val now = serverClock.now()
        val current = list
        if (current == null) {
            val shown = problem
            return when {
                shown is Problem.Waiting -> AirportFlightsUiState.Waiting(waitingUntil(shown, now))
                loading -> AirportFlightsUiState.Loading
                shown is Problem.Failed -> AirportFlightsUiState.Failed(shown.error, shown.canRetry)
                shown is Problem.PageFailed -> AirportFlightsUiState.Failed(shown.error, canRetry = false)
                shown == Problem.ReloadOffered -> AirportFlightsUiState.ReloadOffered
                shown == Problem.AllowanceExhausted -> AirportFlightsUiState.AllowanceExhausted
                pro == true -> AirportFlightsUiState.Loading
                else -> AirportFlightsUiState.Ready
            }
        }

        return AirportFlightsUiState.Loaded(
            display = AirportFlightsDisplay.from(current, repo.pass.value, airportId, airportTimeZone, now, deviceZone()),
            refreshing = loading,
            refreshEnabled = !isRefreshBlocked(),
            pagingDirections = pendingPages.keys.toSet(),
            notice = when (val shown = problem) {
                null -> null
                is Problem.Failed -> AirportFlightsUiState.Notice.Failed(shown.error, shown.canRetry)
                is Problem.PageFailed -> AirportFlightsUiState.Notice.Failed(shown.error, canRetry = false)
                Problem.ReloadOffered -> AirportFlightsUiState.Notice.ReloadOffered
                Problem.AllowanceExhausted -> AirportFlightsUiState.Notice.AllowanceExhausted
                is Problem.Waiting -> AirportFlightsUiState.Notice.Waiting(waitingUntil(shown, now))
            },
        )
    }

    private fun waitingUntil(waiting: Problem.Waiting, now: Instant): ClockTime {
        val at = now.plusMillis(waiting.untilElapsed - monotonicClock.elapsed())
        return displayZone().clockTime(at, now)
    }

    companion object {
        private const val DEFAULT_REFRESH_MS = 60_000L
        private const val MAX_BACKOFF_MS = 10 * 60_000L
        private const val TICK_MS = 60_000L
        private val TAG = logTag("Airport", "Flights", "Controller")
    }
}
