package eu.darken.apl.feeder.core.server

import eu.darken.apl.common.datastore.value
import eu.darken.apl.common.debug.logging.Logging.Priority.INFO
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.feeder.core.config.FeederSettings
import eu.darken.apl.feeder.core.server.FeederServerState.Refusal
import eu.darken.apl.feeder.core.server.FeederServerState.Upload
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.access.AccessState
import eu.darken.apl.server.api.FeederMonitorEntry
import eu.darken.apl.server.api.FeederMonitorEvent
import eu.darken.apl.server.api.FeederMonitorEventsResponse
import eu.darken.apl.server.api.FeederMonitorListResponse
import eu.darken.apl.server.api.FeederMonitorUpsertRequest
import eu.darken.apl.server.api.FeederMonitorUpsertResult
import eu.darken.apl.server.api.ServerApiException
import eu.darken.apl.server.api.ServerCodes
import eu.darken.apl.server.api.ServerEndpoint
import eu.darken.apl.server.api.retryAfter
import eu.darken.apl.server.session.SessionManager
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Brings the server's feeder monitors in line with the local feeders and follows their state through
 * the server's events. The local feeders are what the user wants; what the server answered goes into
 * [FeederServerStore] only.
 *
 * Every create and threshold change is idempotent on the server, so a run cut short anywhere is picked
 * up by the next one.
 */
@Singleton
class FeederMonitorSync @Inject constructor(
    private val sessionManager: SessionManager,
    private val endpoint: ServerEndpoint,
    private val accessRepo: AccessRepo,
    private val store: FeederServerStore,
    private val feederSettings: FeederSettings,
    private val serverClock: ServerClock,
) {

    sealed interface Result {
        /** The server offers no feeder monitoring to the installation the session belongs to. */
        data object Inactive : Result

        /**
         * [moreWork] means another run should follow right away, [resumeAt] that uploads or reads are
         * held back until then.
         */
        data class Done(val moreWork: Boolean, val resumeAt: Instant? = null) : Result

        /** The session changed to another installation during the run, the server state was dropped. */
        data object Aborted : Result
    }

    private class IdentityChangedException(val scope: String, val actual: String) :
        Exception("Run for $scope, the session is now $actual")

    private val runLock = Mutex()

    /** A [foreground] run skips the list read when the last one is recent and nothing changed since. */
    suspend fun run(reason: String, foreground: Boolean = false): Result = runLock.withLock {
        log(TAG) { "run($reason, foreground=$foreground)" }
        val result = try {
            execute(foreground)
        } catch (e: IdentityChangedException) {
            log(TAG, WARN) { "run($reason): ${e.message}, dropping the server state" }
            store.wipe()
            Result.Aborted
        }
        log(TAG) { "run($reason): $result" }
        result
    }

    private suspend fun execute(foreground: Boolean): Result {
        accessRepo.awaitLoaded()
        if (accessRepo.state.value?.offersFeederMonitor != true) return Result.Inactive

        val installationId = sessionManager.authedAs { installationId, _ -> installationId }
        val access = accessRepo.state.value
        if (access == null || !access.offersFeederMonitor || access.installationId != installationId) {
            log(TAG) { "execute(): access is not for $installationId or has no feeder monitoring" }
            return Result.Inactive
        }

        val run = Run(installationId, access, foreground)
        run.enterScope()
        if (run.hydrate()) {
            run.uploads()
            run.pollEvents()
            run.refreshList()
            if (run.reuploadPending) run.uploads()
        }
        return run.result()
    }

    private enum class ListMode {
        /** Monitors and cursor as listed, for a fresh state or a cursor the server no longer serves. */
        REPLACE,

        /** Per monitor the newer state by event seq; the cursor stays. */
        MERGE,
    }

    private sealed interface Attempt<out T> {
        data class Success<T>(val value: T) : Attempt<T>
        data class Refused(val error: ServerApiException) : Attempt<Nothing>

        /** Failed twice without telling whether the server applied it. */
        data object Uncertain : Attempt<Nothing>
        data object CapReached : Attempt<Nothing>
    }

    /** One run for the installation [scope]; every request is made as it. */
    private inner class Run(
        private val scope: String,
        private val access: AccessState,
        private val foreground: Boolean,
    ) {
        private var mutationsLeft = MAX_MUTATIONS_PER_RUN
        private var moreWork = false
        private var readsDeferred = false
        private var listRead = false

        /** The list turned out expired after this run's upload pass. */
        var reuploadPending = false
            private set

        private fun nowMillis(): Long = serverClock.now().toEpochMilli()

        private suspend fun <T> request(block: suspend (token: String) -> T): T =
            sessionManager.authedAs { installationId, token ->
                if (installationId != scope) throw IdentityChangedException(scope, installationId)
                block(token)
            }

        /** Counts a PUT or DELETE against the run's cap; false once it is used up. */
        private fun spend(): Boolean {
            if (mutationsLeft <= 0) {
                moreWork = true
                return false
            }
            mutationsLeft--
            return true
        }

        suspend fun enterScope() {
            val stored = store.current().installationId
            if (stored == scope) return
            if (stored != null) log(TAG, WARN) { "enterScope(): server state of $stored, starting over as $scope" }
            store.update { if (it.installationId == scope) it else FeederServerState(installationId = scope) }
        }

        private fun readsHeldBack(state: FeederServerState): Boolean {
            val notBefore = state.readsNotBefore ?: return false
            if (notBefore <= nowMillis()) return false
            log(TAG) { "reads held back until ${Instant.ofEpochMilli(notBefore)}" }
            readsDeferred = true
            return true
        }

        private suspend fun deferReads(e: ServerApiException) {
            val wait = e.readDeferral() ?: throw e
            val until = nowMillis() + wait.toMillis()
            log(TAG, WARN) { "${e.code}, holding reads back until ${Instant.ofEpochMilli(until)}" }
            store.update { it.copy(readsNotBefore = until) }
            readsDeferred = true
        }

        /** False while the server's monitors are unknown, nothing else can run then. */
        suspend fun hydrate(): Boolean {
            val state = store.current()
            if (state.cursor != null) return true
            if (readsHeldBack(state)) return false
            return try {
                log(TAG) { "hydrate()" }
                readList(ListMode.REPLACE)
                true
            } catch (e: ServerApiException) {
                deferReads(e)
                false
            }
        }

        private suspend fun desired(): List<DesiredFeederMonitor> =
            DesiredFeederMonitor.from(feederSettings.feederGroup.value().configs)

        private fun refusalContext(desired: List<DesiredFeederMonitor>, state: FeederServerState): String = buildString {
            desired.joinTo(this, ",") { "${it.feederId}:${it.offlineAfterMinutes}" }
            append('|').append(access.limits.maxActiveFeederMonitors).append('/').append(access.limits.maxStoredFeederMonitors)
            append('|').append(state.limits?.maxActiveFeeders).append('/').append(state.limits?.maxStoredFeeders)
        }

        suspend fun uploads() {
            reuploadPending = false
            var state = store.current()
            if (state.cursor == null) return
            val notBefore = state.uploadsNotBefore
            if (notBefore != null && notBefore > nowMillis()) {
                log(TAG) { "uploads(): held back until ${Instant.ofEpochMilli(notBefore)}" }
                return
            }
            val desired = desired()
            val context = refusalContext(desired, state)
            if (state.refusalContext != context) {
                val wanted = desired.map { it.feederId }.toSet()
                state = store.update { s ->
                    s.copy(
                        uploads = s.uploads.filterKeys { it in wanted }.mapValues { it.value.copy(refusal = null) },
                        refusalContext = context,
                    )
                }
            }
            try {
                if (!deleteMonitors(state, desired)) return
                putMonitors(state, desired)
            } catch (e: ServerApiException) {
                val wait = e.uploadDeferral() ?: throw e
                val until = nowMillis() + wait.toMillis()
                log(TAG, WARN) { "uploads(): ${e.code}, holding uploads back until ${Instant.ofEpochMilli(until)}" }
                store.update { it.copy(uploadsNotBefore = until) }
            }
        }

        /** Sends the request, once more if the outcome is unknown; PUT and DELETE are safe to repeat. */
        private suspend fun <T> mutate(label: String, block: suspend (token: String) -> T): Attempt<T> {
            var retried = false
            while (true) {
                if (!spend()) {
                    log(TAG) { "$label: no mutations left in this run" }
                    return Attempt.CapReached
                }
                val failure: Exception = try {
                    return Attempt.Success(request(block))
                } catch (e: IOException) {
                    currentCoroutineContext().ensureActive()
                    e
                } catch (e: ServerApiException) {
                    if (!e.isUncertain) return Attempt.Refused(e)
                    e
                }
                if (retried) {
                    log(TAG, WARN) { "$label: outcome unknown twice, leaving it to the next run: ${failure.asLog()}" }
                    return Attempt.Uncertain
                }
                log(TAG, WARN) { "$label: outcome unknown, retrying: $failure" }
                retried = true
            }
        }

        /** False when uploads have to stop for this run. */
        private suspend fun deleteMonitors(state: FeederServerState, desired: List<DesiredFeederMonitor>): Boolean {
            val wanted = desired.map { it.feederId }.toSet()
            for (feederId in state.monitors.keys.filter { it !in wanted }) {
                when (val attempt = mutate("delete($feederId)") { endpoint.deleteFeederMonitor(it, feederId) }) {
                    is Attempt.Success -> {}
                    is Attempt.Refused -> if (attempt.error.code != ServerCodes.FEEDER_NOT_MONITORED) throw attempt.error
                    Attempt.Uncertain -> {
                        moreWork = true
                        return false
                    }

                    Attempt.CapReached -> return false
                }
                log(TAG, INFO) { "deleteMonitors(): $feederId" }
                // The server may have activated a monitor that was over the limit, only a list read tells
                store.update { s ->
                    s.copy(monitors = s.monitors - feederId, uploads = s.uploads - feederId, listRevision = null)
                }
            }
            return true
        }

        private suspend fun putMonitors(state: FeederServerState, desired: List<DesiredFeederMonitor>) {
            val createsNotBefore = state.createsNotBefore
            var createsStopped = false
            if (createsNotBefore != null && createsNotBefore > nowMillis()) {
                log(TAG) { "putMonitors(): creates held back until ${Instant.ofEpochMilli(createsNotBefore)}" }
                createsStopped = true
            }

            desired.forEachIndexed { index, wanted ->
                val feederId = wanted.feederId
                val monitor = state.monitors[feederId]
                val refusal = state.uploads[feederId]?.refusal
                val isCreate = monitor == null
                val skip = if (monitor == null) {
                    createsStopped || refusal == Refusal.STORAGE_LIMIT || refusal == Refusal.INVALID
                } else {
                    monitor.offlineAfterMinutes == wanted.offlineAfterMinutes || refusal == Refusal.INVALID
                }
                if (skip) return@forEachIndexed

                val body = FeederMonitorUpsertRequest(offlineAfterMinutes = wanted.offlineAfterMinutes)
                when (val attempt = mutate("put($feederId)") { endpoint.upsertFeederMonitor(it, feederId, body) }) {
                    is Attempt.Success -> acknowledge(feederId, attempt.value)
                    is Attempt.Refused -> {
                        val error = attempt.error
                        when (error.code) {
                            ServerCodes.INVALID_REQUEST -> {
                                log(TAG, WARN) { "putMonitors(): $feederId refused as invalid" }
                                refuse(setOf(feederId), Refusal.INVALID)
                            }

                            ServerCodes.FEEDER_MONITOR_LIMIT -> {
                                val refused = desired.drop(index)
                                    .map { it.feederId }
                                    .filter { it !in state.monitors }
                                    .toSet()
                                log(TAG, WARN) { "putMonitors(): no room to store $refused" }
                                refuse(refused, Refusal.STORAGE_LIMIT)
                                createsStopped = true
                            }

                            ServerCodes.FEEDER_MONITORING_UNAVAILABLE -> {
                                val until = nowMillis() + error.retryAfter.toMillis()
                                log(TAG, WARN) { "putMonitors(): server full, holding creates back until ${Instant.ofEpochMilli(until)}" }
                                store.update { s ->
                                    val uploads = if (isCreate) {
                                        val upload = s.uploads[feederId] ?: Upload()
                                        s.uploads + (feederId to upload.copy(refusal = Refusal.SERVER_FULL))
                                    } else {
                                        s.uploads
                                    }
                                    s.copy(uploads = uploads, createsNotBefore = until)
                                }
                                createsStopped = true
                            }

                            else -> throw error
                        }
                    }

                    // Creates have to reach the server in order, the next one must not overtake this
                    Attempt.Uncertain -> {
                        moreWork = true
                        return
                    }

                    Attempt.CapReached -> return
                }
            }
        }

        private suspend fun acknowledge(feederId: String, result: FeederMonitorUpsertResult) {
            val remote = result.mutation.feeder
            log(TAG, INFO) {
                "acknowledge(): $feederId ${if (result.created) "created" else "updated"}, ${remote.offlineAfterMinutes} min"
            }
            // The answer's revision also covers what changed for other monitors, only a list read takes those
            store.update { s ->
                s.copy(
                    monitors = s.monitors + (feederId to merged(s.monitors[feederId], remote)),
                    uploads = s.uploads + (feederId to Upload(syncedOfflineAfterMinutes = remote.offlineAfterMinutes)),
                    listRevision = null,
                )
            }
        }

        private suspend fun refuse(feederIds: Set<String>, refusal: Refusal) {
            store.update { s ->
                val uploads = s.uploads.toMutableMap()
                feederIds.forEach { feederId ->
                    val upload = uploads[feederId] ?: Upload()
                    // A feeder refused as invalid stays refused as such
                    if (upload.refusal == Refusal.INVALID && refusal != Refusal.INVALID) return@forEach
                    uploads[feederId] = upload.copy(refusal = refusal)
                }
                s.copy(uploads = uploads)
            }
        }

        suspend fun pollEvents() {
            val state = store.current()
            if (state.cursor == null || readsHeldBack(state)) return
            try {
                pageEvents()
            } catch (e: ServerApiException) {
                deferReads(e)
            }
        }

        private suspend fun pageEvents() {
            var pages = 0
            while (true) {
                if (pages >= MAX_EVENT_PAGES_PER_RUN) {
                    log(TAG) { "pageEvents(): stopping after $pages pages" }
                    moreWork = true
                    return
                }
                pages++
                val cursor = store.current().cursor ?: return
                val page = try {
                    request { endpoint.feederMonitorEvents(it, after = cursor, limit = EVENT_PAGE_LIMIT) }
                } catch (e: ServerApiException) {
                    if (e.code != ServerCodes.CURSOR_EXPIRED) throw e
                    log(TAG, WARN) { "pageEvents(): cursor $cursor expired, taking the state from the list" }
                    readList(ListMode.REPLACE)
                    continue
                }
                if (applyPage(page)) readList(ListMode.MERGE)
                if (!page.hasMore) return
            }
        }

        /** Applies the page's events together with its cursor. Returns whether it names another list revision. */
        private suspend fun applyPage(page: FeederMonitorEventsResponse): Boolean {
            var applied = 0
            var revisionChanged = false
            store.update { s ->
                applied = 0
                revisionChanged = page.revision != s.listRevision
                val monitors = s.monitors.toMutableMap()
                val keys = s.monitors.entries.associate { it.value.monitorId to it.key }
                page.events.sortedBy { it.seq }.forEach { event ->
                    // Events of a monitor this app does not hold are history only
                    val key = keys[event.monitorId] ?: return@forEach
                    val entry = monitors.getValue(key)
                    if (event.seq <= entry.eventSeq) return@forEach
                    monitors[key] = entry.applying(event).copy(eventSeq = event.seq)
                    applied++
                }
                s.copy(monitors = monitors, cursor = page.cursor)
            }
            log(TAG) { "applyPage(): ${page.events.size} events, $applied applied, cursor ${page.cursor}" }
            return revisionChanged
        }

        suspend fun refreshList() {
            if (readsDeferred) return
            val state = store.current()
            if (state.cursor == null) return
            if (state.listRevision != null) {
                if (listRead) return
                val last = state.lastListReadAt
                if (foreground && last != null && nowMillis() - last in 0L until FOREGROUND_LIST_MAX_AGE_MS) {
                    log(TAG) { "refreshList(): read ${nowMillis() - last} ms ago, skipping" }
                    return
                }
            }
            if (readsHeldBack(state)) return
            try {
                readList(ListMode.MERGE)
            } catch (e: ServerApiException) {
                deferReads(e)
            }
        }

        private suspend fun readList(mode: ListMode) {
            log(TAG) { "readList($mode)" }
            applyList(request { endpoint.feederMonitorList(it) }, mode)
        }

        private suspend fun applyList(list: FeederMonitorListResponse, mode: ListMode) {
            val now = nowMillis()
            // The server deleted every monitor, they are all uploaded again
            val expired = list.state == LIST_EXPIRED
            store.update { s ->
                val listed = if (expired) emptyList() else list.feeders
                val monitors = listed.associate { remote ->
                    val key = remote.feederId.toFeederMonitorId() ?: remote.feederId.lowercase()
                    key to if (mode == ListMode.REPLACE) remote else merged(s.monitors[key], remote)
                }
                s.copy(
                    listRevision = list.revision,
                    cursor = if (mode == ListMode.REPLACE || expired || s.cursor == null) list.cursor else s.cursor,
                    listState = list.state,
                    limits = list.limits,
                    sampleIntervalSeconds = list.sampleIntervalSeconds,
                    lastListReadAt = now,
                    monitors = monitors,
                    uploads = if (expired) emptyMap() else s.uploads,
                    refusalContext = if (expired) null else s.refusalContext,
                )
            }
            listRead = true
            log(TAG) { "applyList($mode): revision ${list.revision}, ${list.feeders.size} monitors, state ${list.state}" }
            if (expired) {
                log(TAG, INFO) { "applyList(): the server's monitors expired, uploading them again" }
                reuploadPending = true
            }
        }

        suspend fun result(): Result.Done {
            val state = store.current()
            val now = nowMillis()
            val resumeAt = listOfNotNull(state.uploadsNotBefore, state.createsNotBefore, state.readsNotBefore)
                .filter { it > now }
                .minOrNull()
                ?.let { Instant.ofEpochMilli(it) }
            return Result.Done(moreWork = moreWork, resumeAt = resumeAt)
        }
    }

    companion object {
        private const val MAX_MUTATIONS_PER_RUN = 20
        private const val MAX_EVENT_PAGES_PER_RUN = 10
        private const val EVENT_PAGE_LIMIT = 200
        private const val FOREGROUND_LIST_MAX_AGE_MS = 60_000L

        private const val LIST_EXPIRED = "expired"
        private const val HEALTH_ONLINE = "online"
        private const val HEALTH_OFFLINE = "offline"
        private const val EVENT_OFFLINE = "offline"
        private const val EVENT_ONLINE = "online"
        private const val EVENT_PROBLEM_STARTED = "problem_started"
        private const val EVENT_PROBLEM_CLEARED = "problem_cleared"

        private val TAG = logTag("Feeder", "Server", "Sync")

        /**
         * The stored state wins only when it is newer for the same monitor: it already took events this
         * answer does not reflect yet. Whatever only a list read carries comes from [remote] either way.
         */
        private fun merged(stored: FeederMonitorEntry?, remote: FeederMonitorEntry): FeederMonitorEntry {
            if (stored == null || stored.monitorId != remote.monitorId || stored.eventSeq <= remote.eventSeq) return remote
            return remote.copy(
                health = stored.health,
                healthSince = stored.healthSince,
                unknownReason = stored.unknownReason,
                problems = stored.problems,
                eventSeq = stored.eventSeq,
            )
        }

        /** Coverage gaps and kinds this build does not know change nothing. */
        private fun FeederMonitorEntry.applying(event: FeederMonitorEvent): FeederMonitorEntry = when (event.kind) {
            EVENT_OFFLINE -> copy(health = HEALTH_OFFLINE, healthSince = event.since ?: event.observedAt, unknownReason = null)
            EVENT_ONLINE -> copy(health = HEALTH_ONLINE, healthSince = event.observedAt, unknownReason = null)
            EVENT_PROBLEM_STARTED -> event.problem?.takeIf { it !in problems }?.let { copy(problems = problems + it) } ?: this
            EVENT_PROBLEM_CLEARED -> event.problem?.let { copy(problems = problems - it) } ?: this
            else -> this
        }

        /** The server may or may not have applied the request. */
        private val ServerApiException.isUncertain: Boolean
            get() = code == ServerCodes.DATABASE_UNAVAILABLE ||
                    (status >= 500 && code != ServerCodes.FEEDER_MONITORING_UNAVAILABLE)

        /** Refusals of a write that hold uploads back until the server's hint, instead of failing the run. */
        private fun ServerApiException.uploadDeferral() = if (status == 429) retryAfter else null

        /** Refusals of a read that hold reads back until the server's hint, instead of failing the run. */
        private fun ServerApiException.readDeferral() =
            if (status == 429 || code == ServerCodes.DATABASE_UNAVAILABLE) retryAfter else null
    }
}
