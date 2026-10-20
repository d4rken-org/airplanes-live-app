package eu.darken.apl.watch.core.server

import eu.darken.apl.common.datastore.value
import eu.darken.apl.common.debug.logging.Logging.Priority.INFO
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.access.AccessState
import eu.darken.apl.server.api.ServerApiException
import eu.darken.apl.server.api.ServerCodes
import eu.darken.apl.server.api.ServerEndpoint
import eu.darken.apl.server.api.WatchEvent
import eu.darken.apl.server.api.WatchEventsResponse
import eu.darken.apl.server.api.WatchListLimits
import eu.darken.apl.server.api.WatchListResponse
import eu.darken.apl.server.api.WatchListWatch
import eu.darken.apl.server.api.WatchMutationResponse
import eu.darken.apl.server.api.WatchRollup
import eu.darken.apl.server.api.WatchRollupsResponse
import eu.darken.apl.server.api.WatchUpsertRequest
import eu.darken.apl.server.api.retryAfter
import eu.darken.apl.server.session.SessionManager
import eu.darken.apl.upgrade.UpgradeRepo
import eu.darken.apl.upgrade.isProNow
import eu.darken.apl.watch.core.WatchId
import eu.darken.apl.watch.core.WatchSettings
import eu.darken.apl.watch.core.alerts.WatchWorkerHelper
import eu.darken.apl.watch.core.db.WatchDatabase
import eu.darken.apl.watch.core.db.server.WatchNotificationOutboxEntity
import eu.darken.apl.watch.core.db.server.WatchNotificationOutboxEntity.Companion.KIND_INDIVIDUAL
import eu.darken.apl.watch.core.db.server.WatchNotificationOutboxEntity.Companion.KIND_SUMMARY
import eu.darken.apl.watch.core.db.server.WatchServerEventEntity
import eu.darken.apl.watch.core.db.server.WatchServerRollupEntity
import eu.darken.apl.watch.core.db.server.WatchServerStateEntity
import eu.darken.apl.watch.core.db.server.WatchServerStateEntity.Companion.UPLOAD_PENDING
import eu.darken.apl.watch.core.db.server.WatchServerStateEntity.Companion.UPLOAD_REFUSED
import eu.darken.apl.watch.core.db.server.WatchServerStateEntity.Companion.UPLOAD_REJECTED_INVALID
import eu.darken.apl.watch.core.db.server.WatchServerStateEntity.Companion.UPLOAD_REJECTED_STORAGE
import eu.darken.apl.watch.core.db.server.WatchServerStateEntity.Companion.UPLOAD_SYNCED
import eu.darken.apl.watch.core.db.server.WatchServerSyncEntity
import eu.darken.apl.watch.core.db.server.WatchServerSyncEntity.Companion.PHASE_HYDRATE
import eu.darken.apl.watch.core.db.server.WatchServerSyncEntity.Companion.PHASE_LIVE
import eu.darken.apl.watch.core.db.server.WatchServerSyncEntity.Companion.PHASE_UPLOAD
import eu.darken.apl.watch.core.db.server.WatchServerTombstoneEntity
import eu.darken.apl.watch.core.toDefinition
import eu.darken.apl.watch.core.types.Watch
import eu.darken.apl.watch.core.types.WatchCheckOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Brings the server's watch list in line with the local watches. The local watches are what the user
 * wants; what the server answered goes into the server tables only and is never written back into
 * them.
 *
 * Everything a run does is derived from the stored rows, so a run cut short anywhere is picked up by
 * the next one: a repeated create or edit with the same definition is answered as a no-op.
 */
@Singleton
class WatchListSync @Inject constructor(
    private val sessionManager: SessionManager,
    private val endpoint: ServerEndpoint,
    private val accessRepo: AccessRepo,
    private val watchDb: WatchDatabase,
    private val serverClock: ServerClock,
    private val alerts: WatchServerAlerts,
    private val watchSettings: WatchSettings,
    private val upgradeRepo: UpgradeRepo,
) {

    sealed interface Result {
        /** The server offers no watch list to the installation the session belongs to. */
        data object Inactive : Result

        /**
         * [moreWork] means another run should follow right away, [resumeAt] that uploads are held
         * back until then.
         */
        data class Done(val moreWork: Boolean, val resumeAt: Instant? = null) : Result

        /** The session changed to another installation during the run, the server state was dropped. */
        data object Aborted : Result
    }

    private class IdentityChangedException(val scope: String, val actual: String) :
        Exception("Run for $scope, the session is now $actual")

    private val runLock = Mutex()

    /** A [periodic] run also refreshes the last sightings from the list. */
    suspend fun run(reason: String, periodic: Boolean = false): Result = runLock.withLock {
        log(TAG) { "run($reason, periodic=$periodic)" }
        val result = try {
            execute(periodic)
        } catch (e: IdentityChangedException) {
            log(TAG, WARN) { "run($reason): ${e.message}, dropping the server state" }
            watchDb.wipeServerState()
            Result.Aborted
        }
        log(TAG) { "run($reason): $result" }
        result
    }

    private suspend fun execute(periodic: Boolean): Result {
        accessRepo.awaitLoaded()
        if (accessRepo.state.value?.offersWatchList != true) return Result.Inactive

        val installationId = sessionManager.authedAs { installationId, _ -> installationId }
        val access = accessRepo.state.value
        if (access == null || !access.offersWatchList || access.installationId != installationId) {
            log(TAG) { "execute(): access is not for $installationId or has no watch list" }
            return Result.Inactive
        }

        val run = Run(installationId, access, periodic)
        run.enterScope()
        run.flushNotificationOutbox()
        run.uploads()
        run.pollEvents()
        run.flushNotificationOutbox()
        run.syncRollups()
        return run.result()
    }

    private class Upload(
        val body: WatchUpsertRequest,
        val isCreate: Boolean,
        val definitionRevision: Long,
    )

    private enum class ListMode {
        /** Takes the presence unless the stored one is newer for the same revision. */
        HYDRATE,

        /** Definitions and statuses; the observed state only of a watch whose revision changed. */
        REREAD,

        /**
         * Like [REREAD], plus the last sighting, and the presence where the list holds no event past
         * the stored cursor: taking one of those would skip its alert.
         */
        REFRESH,

        /** Everything including the event cursor, for a cursor the server no longer serves. */
        REPLACE,
    }

    /**
     * One events poll run, from the last poll that read up to the newest event until the next one does.
     * A run cut short or stopped by the page cap is resumed by the next sync run under the same [id].
     */
    private class PollRun(
        val id: String,
        val startedAt: Instant,
        val lastPollAt: Instant?,
        val expectedGap: Duration,
    )

    /** One run for the installation [scope]; every request is made as it, every write is for it. */
    private inner class Run(
        private val scope: String,
        private val access: AccessState,
        private val periodic: Boolean,
    ) {
        private var mutationsLeft = MAX_MUTATIONS_PER_RUN
        private var created = 0
        private var edited = 0
        private var exhausted = false
        private var moreWork = false
        private var resumeAt: Instant? = null
        private var listLimits: WatchListLimits? = null
        private var eventsListRead: WatchListResponse? = null
        private var listApplied = false

        private val maxStored: Int?
            get() = access.limits.maxStoredWatches ?: listLimits?.maxStoredWatches

        private fun now(): Instant = serverClock.now()

        private suspend fun <T> request(block: suspend (token: String) -> T): T =
            sessionManager.authedAs { installationId, token ->
                if (installationId != scope) throw IdentityChangedException(scope, installationId)
                block(token)
            }

        /** Counts a PUT or DELETE against the run's cap; false once it is used up. */
        private fun spend(): Boolean {
            if (mutationsLeft <= 0) {
                exhausted = true
                moreWork = true
                return false
            }
            mutationsLeft--
            return true
        }

        suspend fun enterScope() {
            if (watchDb.server.hasOtherInstallation(scope)) {
                log(TAG, WARN) { "enterScope(): server state of another installation, starting over as $scope" }
                watchDb.wipeServerState()
            }
            watchDb.serverTransaction {
                if (server.getSync(scope) == null) server.upsertSync(freshSync())
            }
        }

        private fun freshSync() = WatchServerSyncEntity(installationId = scope, phase = PHASE_HYDRATE, updatedAt = now())

        private suspend fun WatchDatabase.ServerWriter.writeSync(change: (WatchServerSyncEntity) -> WatchServerSyncEntity) {
            val current = server.getSync(scope) ?: freshSync()
            server.upsertSync(change(current).copy(updatedAt = now()))
        }

        private suspend fun updateSync(change: (WatchServerSyncEntity) -> WatchServerSyncEntity) =
            watchDb.serverTransaction { writeSync(change) }

        private suspend fun setPhase(phase: String) = updateSync { it.copy(phase = phase) }

        /** Posts every due alert; a row is removed only after its post, a crash in between posts it again. */
        suspend fun flushNotificationOutbox() {
            // A poll run's summary still grows until the run caught up, posting it now would split it
            val unfinished = watchDb.server.getSync(scope)?.pollRunId?.let { WatchNotificationOutboxEntity.summaryId(it) }
            for (row in watchDb.server.getOutbox()) {
                if (row.id == unfinished) {
                    log(TAG) { "flushNotificationOutbox(): ${row.id} waits for its poll run to catch up" }
                    continue
                }
                try {
                    post(row)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log(TAG, WARN) { "flushNotificationOutbox(): ${row.id} failed, keeping it: ${e.asLog()}" }
                    continue
                }
                watchDb.server.deleteOutbox(row.id)
            }
        }

        private suspend fun post(row: WatchNotificationOutboxEntity) {
            val watches = watchDb.serverTransaction { watches() }.associateBy { it.id }
            when (row.kind) {
                KIND_INDIVIDUAL -> {
                    val watch = row.watchId?.let { watches[it] }
                    if (watch == null || !watch.isNotificationEnabled) {
                        log(TAG) { "post(): dropping ${row.id}, the watch is gone or quiet" }
                        return
                    }
                    val aircraftIds = row.aircraftIds.splitCsv()
                    alerts.postAlert(watch, aircraftIds, row.totalMatching ?: aircraftIds.size)
                }

                KIND_SUMMARY -> {
                    val notifying = row.summaryWatchIds.splitCsv().count { watches[it]?.isNotificationEnabled == true }
                    if (notifying == 0) {
                        log(TAG) { "post(): dropping ${row.id}, none of its watches alerts anymore" }
                        return
                    }
                    alerts.postSummary(sightings = row.summaryCount, watches = notifying)
                }

                else -> log(TAG, WARN) { "post(): dropping ${row.id} of unknown kind ${row.kind}" }
            }
        }

        suspend fun uploads() {
            val sync = watchDb.server.getSync(scope) ?: freshSync()
            val notBefore = sync.uploadsNotBefore
            if (notBefore != null && notBefore.isAfter(now())) {
                log(TAG) { "uploads(): held back until $notBefore" }
                resumeAt = notBefore
                return
            }
            try {
                if (sync.phase == PHASE_HYDRATE) {
                    hydrate()
                    setPhase(PHASE_UPLOAD)
                }
                if (!deleteTombstones()) return
                if (!putWatches()) return
                if (sync.phase != PHASE_LIVE && watchDb.server.getTombstones(scope).isEmpty()) setPhase(PHASE_LIVE)
            } catch (e: ServerApiException) {
                val wait = e.uploadDeferral() ?: throw e
                val until = now().plus(wait)
                log(TAG, WARN) { "uploads(): ${e.code}, holding uploads back until $until" }
                updateSync { it.copy(uploadsNotBefore = until) }
                resumeAt = until
            } finally {
                if (created + edited > 0) log(TAG, INFO) { "uploads(): $created created, $edited edited" }
            }
        }

        /** Also renews the server's monitoring lease, so it runs while uploads are held back too. */
        suspend fun pollEvents() {
            val sync = watchDb.server.getSync(scope) ?: return
            if (sync.eventCursor == null) {
                log(TAG) { "pollEvents(): the list was not read yet, no cursor to poll from" }
                return
            }
            val notBefore = sync.eventsNotBefore
            if (notBefore != null && notBefore.isAfter(now())) {
                log(TAG) { "pollEvents(): held back until $notBefore" }
                return
            }
            val interval = WatchWorkerHelper.effectiveInterval(
                watchSettings.watchMonitorInterval.value(),
                upgradeRepo.isProNow(),
            )
            val resumed = sync.pollRunId
            if (resumed != null) {
                log(TAG) { "pollEvents(): resuming poll run $resumed, ${sync.pollRunIndividual} alerts used" }
            }
            val poll = PollRun(
                id = resumed ?: UUID.randomUUID().toString(),
                startedAt = now(),
                lastPollAt = sync.lastPollAt,
                expectedGap = WatchAlertCatchUp.expectedGap(interval),
            )
            try {
                if (pageEvents(poll)) refreshQuietState()
            } catch (e: ServerApiException) {
                val wait = e.readDeferral() ?: throw e
                val until = now().plus(wait)
                log(TAG, WARN) { "pollEvents(): ${e.code}, holding polls back until $until" }
                updateSync { it.copy(eventsNotBefore = until) }
            }
        }

        /** True when the run read up to the newest event. */
        private suspend fun pageEvents(poll: PollRun): Boolean {
            var pages = 0
            while (true) {
                if (pages >= MAX_EVENT_PAGES_PER_RUN) {
                    log(TAG) { "pageEvents(): stopping after $pages pages" }
                    moreWork = true
                    return false
                }
                pages++
                val cursor = watchDb.server.getSync(scope)?.eventCursor ?: return false
                val page = try {
                    request { endpoint.watchEvents(it, after = cursor, limit = EVENT_PAGE_LIMIT) }
                } catch (e: ServerApiException) {
                    if (e.code != ServerCodes.CURSOR_EXPIRED) throw e
                    log(TAG, WARN) { "pageEvents(): cursor $cursor expired, taking the state from the list" }
                    readList(ListMode.REPLACE)
                    continue
                }
                if (applyPage(page, poll)) readList(ListMode.REREAD)
                if (!page.hasMore) return true
            }
        }

        private suspend fun readList(mode: ListMode) {
            log(TAG) { "readList($mode)" }
            val list = request { endpoint.watchList(it) }
            applyList(list, mode)
            eventsListRead = list
        }

        /** Reuses a list read by this events phase, a periodic run reads one otherwise. */
        private suspend fun refreshQuietState() {
            val list = eventsListRead ?: if (periodic) request { endpoint.watchList(it) } else return
            log(TAG) { "refreshQuietState()" }
            applyList(list, ListMode.REFRESH)
        }

        /**
         * Stores the page, applies its events of the current revision of each watch and queues their
         * alerts, all together with the cursor. Returns whether the page names another list revision.
         */
        private suspend fun applyPage(page: WatchEventsResponse, poll: PollRun): Boolean = watchDb.serverTransaction {
            val now = now()
            val sync = server.getSync(scope) ?: freshSync()
            val inserted = if (page.events.isEmpty()) {
                emptyList<Long>()
            } else {
                server.insertEvents(page.events.map { it.toEntity(now) })
            }
            val fresh = page.events.filterIndexed { index, _ -> inserted[index] != -1L }.sortedBy { it.seq }

            val watches = watches().associateBy { it.id }
            val mapped = server.getAllStates().filter { it.serverWatchId != null }.associateBy { it.serverWatchId }
            val changed = mutableMapOf<WatchId, WatchServerStateEntity>()
            val alerting = mutableMapOf<Long, Pair<WatchEvent, WatchId>>()
            fresh.forEach { event ->
                val presence = when (event.kind) {
                    EVENT_MATCHED -> PRESENCE_PRESENT
                    EVENT_CLEARED -> PRESENCE_ABSENT
                    else -> return@forEach
                }
                val known = event.watchId?.let { mapped[it] } ?: return@forEach
                val state = changed[known.watchId] ?: known
                if (event.revision?.toLong() != state.serverRevision || event.seq <= state.eventSeq) return@forEach
                val watch = watches[state.watchId] ?: return@forEach
                // Events of a definition the server does not have yet tell nothing about the local one
                if (isDirty(watch, state)) return@forEach

                var applied = state.copy(presence = presence, eventSeq = event.seq, updatedAt = now)
                if (event.kind == EVENT_MATCHED) {
                    applied = applied.copy(
                        lastMatchSeq = event.seq,
                        lastMatchAt = Instant.ofEpochMilli(event.observedAt),
                        lastMatchAircraftIds = event.aircraftIds?.joinToString(","),
                        lastMatchTotal = event.totalMatching,
                    )
                    if (watch.isNotificationEnabled) alerting[event.seq] = event to watch.id
                }
                changed[state.watchId] = applied
            }
            changed.values.forEach { server.upsertState(it) }

            val individualSoFar = sync.pollRunIndividual
            val classification = WatchAlertCatchUp.classify(
                candidates = alerting.values.map { (event, _) ->
                    WatchAlertCatchUp.Candidate(event.seq, Instant.ofEpochMilli(event.observedAt))
                },
                lastPollAt = poll.lastPollAt,
                now = poll.startedAt,
                expectedGap = poll.expectedGap,
                individualSoFar = individualSoFar,
            )
            val outbox = classification.individual.map { candidate ->
                val (event, watchId) = alerting.getValue(candidate.seq)
                WatchNotificationOutboxEntity(
                    id = WatchNotificationOutboxEntity.individualId(event.seq),
                    installationId = scope,
                    kind = KIND_INDIVIDUAL,
                    watchId = watchId,
                    eventSeq = event.seq,
                    aircraftIds = event.aircraftIds?.joinToString(","),
                    totalMatching = event.totalMatching,
                    observedAt = candidate.observedAt,
                    createdAt = now,
                )
            }.toMutableList()
            if (classification.summarized.isNotEmpty()) {
                val id = WatchNotificationOutboxEntity.summaryId(poll.id)
                val existing = server.getOutbox().firstOrNull { it.id == id }
                val summarizedWatches = classification.summarized.map { alerting.getValue(it.seq).second }
                outbox += WatchNotificationOutboxEntity(
                    id = id,
                    installationId = scope,
                    kind = KIND_SUMMARY,
                    observedAt = classification.summarized.maxOf { it.observedAt },
                    summaryCount = (existing?.summaryCount ?: 0) + classification.summarized.size,
                    summaryWatchIds = (existing?.summaryWatchIds.splitCsv() + summarizedWatches)
                        .distinct()
                        .joinToString(","),
                    createdAt = existing?.createdAt ?: now,
                )
            }
            if (outbox.isNotEmpty()) server.upsertOutbox(outbox)

            val caughtUp = !page.hasMore
            writeSync {
                it.copy(
                    eventCursor = page.cursor,
                    pollRunId = if (caughtUp) null else poll.id,
                    pollRunIndividual = if (caughtUp) 0 else individualSoFar + classification.individual.size,
                    lastPollAt = if (caughtUp) now else it.lastPollAt,
                )
            }
            if (caughtUp) server.deleteEventsBefore(now.minus(EVENT_RETENTION))
            log(TAG) {
                "applyPage(): ${page.events.size} events, ${changed.size} watches changed, " +
                        "${classification.individual.size} alerts, ${classification.summarized.size} summarized"
            }
            page.listRevision != sync.listRevision
        }

        private fun WatchEvent.toEntity(receivedAt: Instant) = WatchServerEventEntity(
            seq = seq,
            installationId = scope,
            kind = kind,
            observedAt = Instant.ofEpochMilli(observedAt),
            serverWatchId = watchId,
            watchId = localId,
            revision = revision?.toLong(),
            aircraftIds = aircraftIds?.joinToString(","),
            totalMatching = totalMatching,
            gapFrom = gapFrom?.let { Instant.ofEpochMilli(it) },
            gapUntil = gapUntil?.let { Instant.ofEpochMilli(it) },
            receivedAt = receivedAt,
        )

        /**
         * Only feeds the history views: a failure leaves what the earlier phases committed and the next
         * run picks up from the stored cursor.
         */
        suspend fun syncRollups() {
            val sync = watchDb.server.getSync(scope) ?: return
            if (sync.eventCursor == null) {
                log(TAG) { "syncRollups(): the list was not read yet, no watches to map rollups to" }
                return
            }
            val notBefore = sync.rollupsNotBefore
            if (notBefore != null && notBefore.isAfter(now())) {
                log(TAG) { "syncRollups(): held back until $notBefore" }
                return
            }
            val bootstrapped = try {
                pageRollups()
            } catch (e: ServerApiException) {
                val wait = e.readDeferral()
                if (wait != null) {
                    val until = now().plus(wait)
                    log(TAG, WARN) { "syncRollups(): ${e.code}, holding rollups back until $until" }
                    updateSync { it.copy(rollupsNotBefore = until) }
                } else {
                    log(TAG, WARN) { "syncRollups(): failed, left to the next run: ${e.asLog()}" }
                }
                false
            } catch (e: IOException) {
                log(TAG, WARN) { "syncRollups(): failed, left to the next run: ${e.asLog()}" }
                false
            }
            pruneRollups(unmapped = listApplied || bootstrapped)
        }

        /** True when this run read every rollup from the start. */
        private suspend fun pageRollups(): Boolean {
            var fromStart = (watchDb.server.getSync(scope)?.rollupCursor ?: 0L) == 0L
            var pages = 0
            while (true) {
                if (pages >= MAX_ROLLUP_PAGES_PER_RUN) {
                    log(TAG) { "pageRollups(): stopping after $pages pages" }
                    moreWork = true
                    return false
                }
                pages++
                val cursor = watchDb.server.getSync(scope)?.rollupCursor ?: 0L
                val page = try {
                    request { endpoint.watchRollups(it, after = cursor, limit = ROLLUP_PAGE_LIMIT) }
                } catch (e: ServerApiException) {
                    if (e.code != ServerCodes.CURSOR_EXPIRED || cursor == 0L) throw e
                    log(TAG, WARN) { "pageRollups(): cursor $cursor expired, reading every rollup again" }
                    restartRollups()
                    fromStart = true
                    continue
                }
                applyRollupPage(page)
                if (!page.hasMore) return fromStart
            }
        }

        private suspend fun restartRollups() = watchDb.serverTransaction {
            server.deleteRollupsOf(scope)
            writeSync { it.copy(rollupCursor = 0L) }
        }

        /**
         * Rows of server watches no state maps yet are stored as well, the next list read maps or prunes
         * them; skipping them would lose final hours the cursor has already passed.
         */
        private suspend fun applyRollupPage(page: WatchRollupsResponse) = watchDb.serverTransaction {
            // Upserted in seq order, so the newest copy of an hour in the page wins
            if (page.rollups.isNotEmpty()) server.upsertRollups(page.rollups.sortedBy { it.seq }.map { it.toEntity() })
            writeSync { it.copy(rollupCursor = page.cursor) }
            log(TAG) { "applyRollupPage(): ${page.rollups.size} rollups, cursor ${page.cursor}" }
        }

        private fun WatchRollup.toEntity() = WatchServerRollupEntity(
            serverWatchId = watchId,
            revision = revision.toLong(),
            hour = Instant.ofEpochMilli(hour),
            source = source,
            watchId = localId,
            installationId = scope,
            seq = seq,
            evaluationBasis = evaluationBasis,
            aircraftCount = aircraftCount,
            coverage = coverage,
            coveredMinutes = coveredMinutes,
            isFinal = isFinal,
            lastSeenAt = lastSeenAt?.let { Instant.ofEpochMilli(it) },
            lastSeenAircraftId = lastSeenAircraftId,
        )

        /** With [unmapped], the states are known to be current and rows of server watches none maps go too. */
        private suspend fun pruneRollups(unmapped: Boolean) {
            val (old, orphaned) = watchDb.serverTransaction {
                val old = server.deleteRollupsBefore(now().minus(ROLLUP_RETENTION))
                val orphaned = if (unmapped) {
                    server.deleteRollupsExcept(server.getAllStates().mapNotNull { it.serverWatchId }.toSet())
                } else {
                    0
                }
                old to orphaned
            }
            if (old + orphaned > 0) log(TAG) { "pruneRollups(): $old past the retention, $orphaned unmapped" }
        }

        suspend fun result(): Result.Done {
            if (resumeAt == null && !moreWork) moreWork = hasRemainingWork()
            return Result.Done(moreWork = moreWork, resumeAt = resumeAt)
        }

        private suspend fun hasRemainingWork(): Boolean = watchDb.serverTransaction {
            if (server.getTombstones(scope).isNotEmpty()) return@serverTransaction true
            val states = server.getAllStates().associateBy { it.watchId }
            watches().any { watch ->
                val state = states[watch.id]
                state?.uploadState !in UPLOAD_REFUSED && isDirty(watch, state)
            }
        }

        private suspend fun hydrate() {
            log(TAG) { "hydrate()" }
            applyList(request { endpoint.watchList(it) }, ListMode.HYDRATE)
        }

        private suspend fun reread() {
            log(TAG) { "reread()" }
            applyList(request { endpoint.watchList(it) }, ListMode.REREAD)
        }

        /**
         * Maps the server's watches onto the local ones. How much of the observed state a watch whose
         * revision did not change takes depends on [mode], the stored one may be newer than this answer.
         */
        private suspend fun applyList(list: WatchListResponse, mode: ListMode) {
            listLimits = list.limits
            watchDb.serverTransaction {
                val now = now()
                val storedCursor = server.getSync(scope)?.eventCursor
                val listBehindCursor = storedCursor != null && list.cursor <= storedCursor
                val locals = watches().associateBy { it.id }
                val states = server.getAllStates().associateBy { it.watchId }
                val tombstoned = server.getTombstones(scope).map { it.watchId }.toSet()
                val expired = list.state == LIST_EXPIRED
                val listed = if (expired) emptyList() else list.watches

                val orphans = mutableListOf<WatchServerTombstoneEntity>()
                listed.forEach { remote ->
                    // Its server copy waits for deletion, mapping it again would undo a refused edit
                    if (remote.localId in tombstoned) return@forEach
                    val local = locals[remote.localId]
                    if (local == null) {
                        orphans += WatchServerTombstoneEntity(remote.localId, scope, now)
                        return@forEach
                    }
                    val existing = states[local.id]
                    val sameRevision = existing?.serverWatchId == remote.watchId &&
                            existing.serverRevision == remote.revision.toLong()
                    val takePresence = when (mode) {
                        ListMode.HYDRATE -> !sameRevision || remote.eventSeq >= existing!!.eventSeq
                        ListMode.REREAD -> !sameRevision
                        ListMode.REFRESH -> !sameRevision || listBehindCursor && remote.eventSeq >= existing!!.eventSeq
                        ListMode.REPLACE -> true
                    }
                    val takeLastSeen = takePresence || mode == ListMode.REFRESH
                    val observed = (existing ?: pendingState(local.id, now))
                        .observed(remote, takePresence = takePresence, takeLastSeen = takeLastSeen, now = now)
                    server.upsertState(
                        if (remote.hasDefinitionOf(local)) {
                            observed.copy(uploadState = UPLOAD_SYNCED, ackedDefinitionRevision = local.definitionRevision)
                        } else {
                            observed.copy(
                                uploadState = if (existing?.uploadState == UPLOAD_REJECTED_INVALID) UPLOAD_REJECTED_INVALID else UPLOAD_PENDING,
                                ackedDefinitionRevision = null,
                            )
                        }
                    )
                }
                if (orphans.isNotEmpty()) {
                    log(TAG, INFO) { "applyList(): ${orphans.size} server watches without a local one" }
                    server.insertTombstones(orphans)
                }

                val listedIds = listed.map { it.localId }.toSet()
                val vanished = states.values.filter { it.serverWatchId != null && it.watchId !in listedIds }
                if (vanished.isNotEmpty()) {
                    log(TAG, INFO) { "applyList(): ${vanished.size} watches are gone from the server (state=${list.state})" }
                    vanished.forEach { server.upsertState(it.unmapped(now)) }
                    server.deleteRollupsFor(vanished.map { it.watchId }.toSet())
                }

                writeSync { sync ->
                    sync.copy(
                        listRevision = list.revision,
                        listState = list.state,
                        eventCursor = if (expired || sync.eventCursor == null || mode == ListMode.REPLACE) {
                            list.cursor
                        } else {
                            sync.eventCursor
                        },
                    )
                }
            }
            listApplied = true
        }

        private fun pendingState(watchId: WatchId, now: Instant) = WatchServerStateEntity(
            watchId = watchId,
            installationId = scope,
            uploadState = UPLOAD_PENDING,
            updatedAt = now,
        )

        private fun WatchServerStateEntity.observed(
            remote: WatchListWatch,
            takePresence: Boolean,
            takeLastSeen: Boolean,
            now: Instant,
        ): WatchServerStateEntity {
            // A last match is only known to hold for the server watch and revision it was observed under
            val sameDefinition = serverWatchId == remote.watchId && serverRevision == remote.revision.toLong()
            return copy(
                installationId = scope,
                serverWatchId = remote.watchId,
                serverRevision = remote.revision.toLong(),
                status = remote.status,
                pauseReason = remote.pauseReason,
                backfill = remote.backfill,
                presence = if (takePresence) remote.presence else presence,
                eventSeq = if (takePresence) remote.eventSeq else eventSeq,
                lastSeenAt = if (takeLastSeen) remote.lastSeenAt?.let { Instant.ofEpochMilli(it) } else lastSeenAt,
                lastSeenAircraftId = if (takeLastSeen) remote.lastSeenAircraftId else lastSeenAircraftId,
                lastMatchSeq = lastMatchSeq.takeIf { sameDefinition },
                lastMatchAt = lastMatchAt.takeIf { sameDefinition },
                lastMatchAircraftIds = lastMatchAircraftIds.takeIf { sameDefinition },
                lastMatchTotal = lastMatchTotal.takeIf { sameDefinition },
                updatedAt = now,
            )
        }

        /**
         * The server no longer holds the watch, it is uploaded again as a new one; a refused definition
         * only once it changed. The presence stays as the create's baseline, valid while the local
         * definition is still [WatchServerStateEntity.ackedDefinitionRevision].
         */
        private fun WatchServerStateEntity.unmapped(now: Instant) = WatchServerStateEntity(
            watchId = watchId,
            installationId = scope,
            uploadState = if (uploadState == UPLOAD_REJECTED_INVALID) UPLOAD_REJECTED_INVALID else UPLOAD_PENDING,
            sentGeneration = sentGeneration,
            ackedDefinitionRevision = ackedDefinitionRevision,
            presence = presence?.takeIf { it in CONCLUSIVE_PRESENCE },
            lastSeenAt = lastSeenAt,
            lastSeenAircraftId = lastSeenAircraftId,
            updatedAt = now,
        )

        /** False when the cap stopped it. */
        private suspend fun deleteTombstones(): Boolean {
            for (tombstone in watchDb.server.getTombstones(scope)) {
                if (!spend()) return false
                log(TAG) { "deleteTombstones(): ${tombstone.watchId}" }
                request { endpoint.deleteWatch(it, tombstone.watchId) }
                watchDb.serverTransaction {
                    server.deleteTombstone(tombstone.watchId)
                    // A watch restored under the same id has to be created again
                    server.getState(tombstone.watchId)?.let { server.upsertState(it.unmapped(now())) }
                    // The answer carries no list revision, the stored one is outdated now
                    writeSync { it.copy(listRevision = null) }
                }
            }
            return true
        }

        /** False when the cap stopped it. */
        private suspend fun putWatches(): Boolean {
            val (watches, states) = watchDb.serverTransaction {
                watches().sortedWith(CREATION_ORDER) to server.getAllStates().associateBy { it.watchId }
            }
            for (watch in watches) {
                if (!needsUpload(watch, states[watch.id], watches.size)) continue
                if (!spend()) return false
                upload(watch.id)
                if (exhausted) return false
            }
            return true
        }

        private fun isDirty(watch: Watch, state: WatchServerStateEntity?): Boolean = state == null ||
                state.serverWatchId == null ||
                state.serverRevision == null ||
                state.ackedDefinitionRevision != watch.definitionRevision

        private fun needsUpload(watch: Watch, state: WatchServerStateEntity?, localCount: Int): Boolean {
            if (state == null) return true
            return when (state.uploadState) {
                // Every definition change raises the generation too
                UPLOAD_REJECTED_INVALID -> state.sentGeneration != watch.localGeneration
                UPLOAD_REJECTED_STORAGE -> maxStored?.let { localCount <= it } == true
                else -> isDirty(watch, state)
            }
        }

        private suspend fun upload(id: WatchId) {
            var conflictRetried = false
            var notFoundRetried = false
            while (true) {
                val upload = prepare(id) ?: return
                val mutation = try {
                    request { endpoint.upsertWatch(it, id, upload.body) }.mutation
                } catch (e: ServerApiException) {
                    when (e.code) {
                        ServerCodes.INVALID_REQUEST -> {
                            if (upload.isCreate) {
                                log(TAG, WARN) { "upload($id): refused as invalid" }
                                reject(id, UPLOAD_REJECTED_INVALID)
                            } else {
                                log(TAG, WARN) { "upload($id): edit refused as invalid, deleting the server copy" }
                                retire(id)
                            }
                            return
                        }

                        ServerCodes.STORED_WATCH_LIMIT -> {
                            log(TAG, WARN) { "upload($id): no room to store it" }
                            reject(id, UPLOAD_REJECTED_STORAGE)
                            return
                        }

                        ServerCodes.REVISION_CONFLICT -> {
                            if (conflictRetried) throw e
                            conflictRetried = true
                            reread()
                        }

                        ServerCodes.WATCH_NOT_FOUND -> {
                            if (notFoundRetried) throw e
                            notFoundRetried = true
                            unmap(id)
                        }

                        else -> throw e
                    }
                    if (!spend()) return
                    continue
                }
                acknowledge(id, upload, mutation)
                if (upload.isCreate) created++ else edited++
                return
            }
        }

        /** Builds the request from the watch as it is now and marks it as in flight; null when nothing is to send. */
        private suspend fun prepare(id: WatchId): Upload? = watchDb.serverTransaction {
            val watch = watches().firstOrNull { it.id == id } ?: return@serverTransaction null
            val state = server.getState(id)
            if (state != null && !isDirty(watch, state)) return@serverTransaction null

            val serverRevision = if (state?.serverWatchId != null) state.serverRevision else null
            val isCreate = serverRevision == null
            val definition = watch.toDefinition()
            val body = WatchUpsertRequest(
                type = definition.type,
                value = definition.value,
                latitude = definition.latitude,
                longitude = definition.longitude,
                radiusKm = definition.radiusKm,
                expectedWatchRevision = serverRevision?.toInt(),
                baseline = if (isCreate) baselineOf(watch, state) else null,
            )
            val now = now()
            server.upsertState(
                (state ?: pendingState(id, now)).copy(
                    uploadState = UPLOAD_PENDING,
                    sentGeneration = watch.localGeneration,
                    updatedAt = now,
                )
            )
            Upload(
                body = body,
                isCreate = isCreate,
                definitionRevision = watch.definitionRevision,
            )
        }

        /** What the server last knew about this definition, else the newest conclusive local evidence. */
        private suspend fun baselineOf(watch: Watch, state: WatchServerStateEntity?): String? {
            val known = state?.presence
            if (known in CONCLUSIVE_PRESENCE && state?.ackedDefinitionRevision == watch.definitionRevision) return known
            when (watch.lastCheckOutcome) {
                WatchCheckOutcome.MATCHED -> return PRESENCE_PRESENT
                WatchCheckOutcome.ABSENT -> return PRESENCE_ABSENT
                else -> {}
            }
            val last = watchDb.checks.getLastCheck(watch.id) ?: return null
            return if (last.aircraftcount > 0) PRESENCE_PRESENT else PRESENCE_ABSENT
        }

        /** Acknowledges what was sent, a watch that changed meanwhile stays dirty. */
        private suspend fun acknowledge(id: WatchId, upload: Upload, mutation: WatchMutationResponse) =
            watchDb.serverTransaction {
                val now = now()
                // The answer's list revision also covers what it changed for other watches, e.g. one it
                // promoted to active; only a list read takes those
                writeSync { it.copy(listRevision = null) }
                // Deleted while in flight: its tombstone removes the server copy
                val state = server.getState(id) ?: return@serverTransaction
                val remote = mutation.watch
                val revisionChanged = state.serverWatchId != remote.watchId ||
                        state.serverRevision != remote.revision.toLong()
                server.upsertState(
                    state.observed(remote, takePresence = revisionChanged, takeLastSeen = revisionChanged, now = now).copy(
                        uploadState = UPLOAD_SYNCED,
                        ackedDefinitionRevision = upload.definitionRevision,
                    )
                )
            }

        private suspend fun reject(id: WatchId, uploadState: String) = watchDb.serverTransaction {
            val state = server.getState(id) ?: return@serverTransaction
            server.upsertState(state.copy(uploadState = uploadState, updatedAt = now()))
        }

        private suspend fun unmap(id: WatchId) = watchDb.serverTransaction {
            val state = server.getState(id) ?: return@serverTransaction
            server.upsertState(state.unmapped(now()))
            server.deleteRollupsFor(setOf(id))
        }

        /**
         * The server keeps the old definition of a refused edit and would go on matching it. Its copy is
         * deleted through a tombstone, and the watch stays refused until it changes again.
         */
        private suspend fun retire(id: WatchId) = watchDb.serverTransaction {
            val state = server.getState(id) ?: return@serverTransaction
            val now = now()
            server.upsertState(state.unmapped(now).copy(uploadState = UPLOAD_REJECTED_INVALID))
            server.insertTombstones(listOf(WatchServerTombstoneEntity(id, scope, now)))
            server.deleteRollupsFor(setOf(id))
        }
    }

    companion object {
        private const val MAX_MUTATIONS_PER_RUN = 40
        private const val MAX_EVENT_PAGES_PER_RUN = 10
        private const val EVENT_PAGE_LIMIT = 200
        private val EVENT_RETENTION: Duration = Duration.ofDays(7)
        private const val MAX_ROLLUP_PAGES_PER_RUN = 5
        private const val ROLLUP_PAGE_LIMIT = 1000
        private val ROLLUP_RETENTION: Duration = Duration.ofDays(31)
        private val LIST_DELETED_DEFERRAL: Duration = Duration.ofHours(24)

        private const val LIST_EXPIRED = "expired"
        private const val TYPE_LOCATION = "location"
        private const val PRESENCE_PRESENT = "present"
        private const val PRESENCE_ABSENT = "absent"
        private const val EVENT_MATCHED = "matched"
        private const val EVENT_CLEARED = "cleared"
        private val CONCLUSIVE_PRESENCE = setOf(PRESENCE_PRESENT, PRESENCE_ABSENT)

        private val CREATION_ORDER = compareBy<Watch>({ it.addedAt }, { it.id })

        private val TAG = logTag("Watch", "List", "Sync")

        /** Refusals that hold uploads back until the server's hint, instead of failing the run. */
        private fun ServerApiException.uploadDeferral(): Duration? = when (code) {
            ServerCodes.WATCH_LIST_DELETED -> LIST_DELETED_DEFERRAL
            ServerCodes.WATCHLIST_UNAVAILABLE,
            ServerCodes.WATCH_MUTATION_BUDGET_EXCEEDED,
            ServerCodes.QUOTA_EXCEEDED,
            ServerCodes.DATABASE_UNAVAILABLE -> retryAfter

            else -> null
        }

        /** Refusals of a read that hold polls back until the server's hint, instead of failing the run. */
        private fun ServerApiException.readDeferral(): Duration? = when (code) {
            ServerCodes.WATCH_LIST_DELETED -> LIST_DELETED_DEFERRAL
            ServerCodes.INSTALLATION_RATE_EXCEEDED,
            ServerCodes.QUOTA_EXCEEDED,
            ServerCodes.DATABASE_UNAVAILABLE -> retryAfter

            else -> null
        }

        private fun String?.splitCsv(): List<String> = this?.split(',')?.filter { it.isNotEmpty() }.orEmpty()

        private fun WatchListWatch.hasDefinitionOf(watch: Watch): Boolean {
            val definition = watch.toDefinition()
            if (type != definition.type) return false
            return if (type == TYPE_LOCATION) {
                latitude == definition.latitude && longitude == definition.longitude && radiusKm == definition.radiusKm
            } else {
                value?.trim()?.uppercase() == definition.value?.trim()?.uppercase()
            }
        }
    }
}
