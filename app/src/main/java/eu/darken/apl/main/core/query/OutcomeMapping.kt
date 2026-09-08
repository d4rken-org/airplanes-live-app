package eu.darken.apl.main.core.query

import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.main.core.aircraft.toAircraft
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.api.BatchResponse
import eu.darken.apl.server.api.QueryMetadata
import eu.darken.apl.server.api.QueryOutcome
import eu.darken.apl.server.api.ViewingResponse
import java.time.Instant

fun ViewingResponse.toSnapshot(serverClock: ServerClock): ViewingSnapshot {
    serverClock.noteServerTime(serverTime)
    val fetchedAt = Instant.ofEpochMilli(serverTime)
    return ViewingSnapshot(
        aircraft = aircraft.map { it.toAircraft(fetchedAt) },
        complete = metadata.complete,
        capped = capped,
        totalMatching = totalMatching,
        snapshot = metadata.toQuerySnapshot(serverTime, serverClock.elapsed()),
        usage = usage,
    )
}

fun BatchResponse.toTermResults(serverClock: ServerClock): BatchResult<TermOutcome> =
    toBatchResult(serverClock) { outcome, details ->
        when (outcome.status) {
            STATUS_ANSWERED -> TermOutcome.Answered(
                aircraft = outcome.aircraftIds.mapNotNull { details[it] },
                complete = outcome.complete,
                capped = outcome.capped,
                totalMatching = outcome.totalMatching,
                expiresAt = outcome.expiresAt?.let { Instant.ofEpochMilli(it) },
                charged = outcome.charged,
            )

            else -> TermOutcome.Rejected(outcome.error?.code ?: STATUS_ERROR)
        }
    }

fun BatchResponse.toWatchResults(serverClock: ServerClock): BatchResult<WatchOutcome> =
    toBatchResult(serverClock) { outcome, details ->
        when (outcome.status) {
            STATUS_MATCHED -> WatchOutcome.Matched(
                aircraft = outcome.aircraftIds.mapNotNull { details[it] },
                totalMatching = outcome.totalMatching,
                capped = outcome.capped,
                expiresAt = outcome.expiresAt?.let { Instant.ofEpochMilli(it) },
            )

            STATUS_ABSENT -> WatchOutcome.Absent(outcome.expiresAt?.let { Instant.ofEpochMilli(it) })
            STATUS_INCONCLUSIVE -> WatchOutcome.Inconclusive(outcome.error?.code)
            else -> WatchOutcome.Rejected(outcome.error?.code ?: STATUS_ERROR)
        }
    }

private fun <O> BatchResponse.toBatchResult(
    serverClock: ServerClock,
    map: (QueryOutcome, Map<String, Aircraft>) -> O,
): BatchResult<O> {
    serverClock.noteServerTime(serverTime)
    val fetchedAt = Instant.ofEpochMilli(serverTime)
    // Aircraft details travel once per batch, outcomes reference them by id
    val details = aircraft.associate { it.id to it.toAircraft(fetchedAt) }
    return BatchResult(
        operationId = operationId,
        replayed = replayed,
        snapshot = metadata?.toQuerySnapshot(serverTime, serverClock.elapsed()),
        outcomes = outcomes.sortedBy { it.index }.map { map(it, details) },
        usage = usage,
    )
}

private fun QueryMetadata.toQuerySnapshot(serverTime: Long, receivedAtElapsed: Long) = QuerySnapshot(
    serverTime = Instant.ofEpochMilli(serverTime),
    receivedAtElapsed = receivedAtElapsed,
    sourceTime = Instant.ofEpochMilli(sourceTime),
    fetchedAt = Instant.ofEpochMilli(fetchedAt),
    expiresAt = Instant.ofEpochMilli(expiresAt),
    complete = complete,
    stale = stale,
    unpositionedAircraft = unpositionedAircraft,
)

private const val STATUS_ANSWERED = "answered"
private const val STATUS_MATCHED = "matched"
private const val STATUS_ABSENT = "absent"
private const val STATUS_INCONCLUSIVE = "inconclusive"
private const val STATUS_ERROR = "error"
