package eu.darken.apl.feeder.core.server

import eu.darken.apl.server.api.FeederMonitorEntry
import eu.darken.apl.server.api.FeederMonitorEvent
import eu.darken.apl.server.api.FeederMonitorEventsResponse
import eu.darken.apl.server.api.FeederMonitorLimits
import eu.darken.apl.server.api.FeederMonitorListResponse
import eu.darken.apl.server.api.FeederMonitorMutationResponse
import eu.darken.apl.server.api.FeederMonitorUpsertRequest
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import java.util.concurrent.CopyOnWriteArrayList

/** A feeder monitoring server of one installation that keeps its state in memory and answers by the API rules. */
class FakeFeederMonitorServer(
    private val json: Json,
    private val now: () -> Long,
) : Dispatcher() {

    class Call(val method: String, val path: String, val body: String) {
        val feederId: String get() = path.removePrefix(FEEDERS_PATH)
    }

    private class Script(val method: String, val pathPrefix: String, var times: Int, val response: () -> MockResponse)

    val calls = CopyOnWriteArrayList<Call>()
    var revision = 0L
    var state = "active"
    var cursor = 0L
    var maxActive = 10
    var maxStored = 50
    var sampleIntervalSeconds: Int? = 300

    /** Creates are refused as server full, threshold changes and deletes still pass. */
    var full = false

    /** In registration order. */
    val monitors = mutableListOf<FeederMonitorEntry>()
    private var nextMonitorId = 1

    val events = mutableListOf<FeederMonitorEvent>()
    var eventPageSize = Int.MAX_VALUE

    /** Polls from before this seq are answered as an expired cursor. */
    var expiredBefore: Long? = null

    private val scripts = mutableListOf<Script>()

    /** The next [times] calls of [method] whose path starts with [pathPrefix] get [response] instead. */
    fun fail(method: String, pathPrefix: String, times: Int = 1, response: () -> MockResponse) {
        scripts += Script(method, pathPrefix, times, response)
    }

    fun clearFailures() = scripts.clear()

    fun monitor(feederId: String): FeederMonitorEntry = monitors.single { it.feederId == feederId.lowercase() }

    /** A health or status change without an event, like the server's own sampling; the revision stays. */
    fun updateMonitor(feederId: String, change: (FeederMonitorEntry) -> FeederMonitorEntry) {
        val index = monitors.indexOfFirst { it.feederId == feederId.lowercase() }
        monitors[index] = change(monitors[index])
    }

    fun seed(feederId: String, offlineAfterMinutes: Int = 2880, health: String = "online"): FeederMonitorEntry {
        monitors += FeederMonitorEntry(
            feederId = feederId.lowercase(),
            monitorId = "monitor-${nextMonitorId++}",
            offlineAfterMinutes = offlineAfterMinutes,
            status = "active",
            createdAt = now(),
            updatedAt = now(),
            health = health,
            healthSince = now(),
            eventSeq = cursor,
        )
        restatus()
        revision++
        return monitor(feederId)
    }

    /**
     * Adds the next event. With [reflect] the monitor it belongs to takes it too, so list reads show it;
     * without, the list stays behind the event.
     */
    fun event(
        kind: String,
        feederId: String,
        observedAt: Long = now(),
        since: Long? = null,
        problem: String? = null,
        monitorId: String = monitor(feederId).monitorId,
        reflect: Boolean = true,
    ): FeederMonitorEvent {
        val event = FeederMonitorEvent(
            seq = cursor + 1,
            kind = kind,
            observedAt = observedAt,
            feederId = feederId.lowercase(),
            monitorId = monitorId,
            since = since,
            problem = problem,
            gapFrom = if (kind == "coverage_gap") observedAt - 300_000 else null,
            gapUntil = if (kind == "coverage_gap") observedAt else null,
        )
        events += event
        cursor = event.seq
        if (reflect) {
            val index = monitors.indexOfFirst { it.monitorId == monitorId }
            if (index >= 0) {
                val monitor = monitors[index]
                monitors[index] = when (kind) {
                    "offline" -> monitor.copy(health = "offline", healthSince = since ?: observedAt, unknownReason = null)
                    "online" -> monitor.copy(health = "online", healthSince = observedAt, unknownReason = null)
                    "problem_started" -> monitor.copy(problems = (monitor.problems + problem!!).distinct())
                    "problem_cleared" -> monitor.copy(problems = monitor.problems - problem!!)
                    else -> monitor
                }.copy(eventSeq = event.seq)
            }
        }
        return event
    }

    @Synchronized
    override fun dispatch(request: RecordedRequest): MockResponse {
        val call = Call(request.method!!, request.path!!, request.body.readUtf8())
        calls += call
        scripts.firstOrNull { it.method == call.method && call.path.startsWith(it.pathPrefix) && it.times > 0 }?.let {
            it.times--
            return it.response()
        }
        return when {
            call.method == "GET" && call.path == LIST_PATH -> ok(list(), FeederMonitorListResponse.serializer())
            call.method == "GET" && call.path.startsWith(EVENTS_PATH) -> pollEvents(call.path)
            call.method == "PUT" && call.path.startsWith(FEEDERS_PATH) -> {
                upsert(call.feederId, json.decodeFromString(FeederMonitorUpsertRequest.serializer(), call.body))
            }

            call.method == "DELETE" && call.path.startsWith(FEEDERS_PATH) -> delete(call.feederId)
            else -> MockResponse().setResponseCode(404)
        }
    }

    /** The first [maxActive] monitors that are active or paused over the limit are active, the rest are paused. */
    private fun restatus(): Boolean {
        var active = 0
        var changed = false
        monitors.indices.forEach { index ->
            val monitor = monitors[index]
            if (monitor.status != "active" && monitor.pauseReason != "over_limit") return@forEach
            val updated = if (active < maxActive) {
                active++
                if (monitor.status == "active") {
                    monitor
                } else {
                    monitor.copy(status = "active", pauseReason = null, health = "unknown", unknownReason = "pending")
                }
            } else if (monitor.pauseReason == "over_limit") {
                monitor
            } else {
                monitor.copy(status = "paused", pauseReason = "over_limit", health = "unknown", unknownReason = "paused")
            }
            if (updated != monitor) {
                monitors[index] = updated
                changed = true
            }
        }
        return changed
    }

    private fun settle() {
        if (restatus()) revision++
    }

    private fun list(): FeederMonitorListResponse {
        settle()
        return FeederMonitorListResponse(
            serverTime = now(),
            revision = revision,
            state = state,
            limits = FeederMonitorLimits(maxActiveFeeders = maxActive, maxStoredFeeders = maxStored),
            feeders = if (state == "expired") emptyList() else monitors.toList(),
            cursor = cursor,
            sampleIntervalSeconds = sampleIntervalSeconds,
        )
    }

    private fun pollEvents(path: String): MockResponse {
        settle()
        val url = "http://fake$path".toHttpUrl()
        val after = url.queryParameter("after")!!.toLong()
        val limit = url.queryParameter("limit")?.toInt() ?: 200
        expiredBefore?.let { if (after < it) return problem(410, "cursor_expired") }
        val pending = events.filter { it.seq > after }.sortedBy { it.seq }
        val page = pending.take(minOf(limit, eventPageSize))
        val body = FeederMonitorEventsResponse(
            serverTime = now(),
            events = page,
            cursor = page.lastOrNull()?.seq ?: after,
            hasMore = pending.size > page.size,
            revision = revision,
        )
        return ok(body, FeederMonitorEventsResponse.serializer())
    }

    private fun upsert(rawId: String, body: FeederMonitorUpsertRequest): MockResponse {
        val feederId = canonical(rawId) ?: return problem(400, "invalid_request")
        val minutes = body.offlineAfterMinutes
        if (minutes != null && minutes !in 15..2880) return problem(400, "invalid_request")
        if (state == "expired") {
            monitors.clear()
            state = "active"
            revision++
        }
        val index = monitors.indexOfFirst { it.feederId == feederId }
        if (index >= 0) {
            val existing = monitors[index]
            if (minutes != null && minutes != existing.offlineAfterMinutes) {
                monitors[index] = existing.copy(offlineAfterMinutes = minutes, updatedAt = now())
                revision++
            }
            return mutation(200, monitors[index])
        }
        if (full) return problem(503, "feeder_monitoring_unavailable", retryAfter = 3600)
        if (monitors.size >= maxStored) return problem(409, "feeder_monitor_limit")
        monitors += FeederMonitorEntry(
            feederId = feederId,
            monitorId = "monitor-${nextMonitorId++}",
            offlineAfterMinutes = minutes ?: 2880,
            status = "active",
            createdAt = now(),
            updatedAt = now(),
            health = "unknown",
            unknownReason = "pending",
            eventSeq = 0,
        )
        restatus()
        revision++
        return mutation(201, monitors.last())
    }

    private fun delete(rawId: String): MockResponse {
        val feederId = canonical(rawId) ?: return problem(400, "invalid_request")
        if (!monitors.removeAll { it.feederId == feederId }) return problem(404, "feeder_not_monitored")
        restatus()
        revision++
        return MockResponse().setResponseCode(204)
    }

    private fun canonical(raw: String): String? {
        if (!UUID_FORMAT.matches(raw)) return null
        val lower = raw.lowercase()
        return lower.takeIf { it != ZERO_UUID }
    }

    private fun mutation(status: Int, entry: FeederMonitorEntry) =
        ok(FeederMonitorMutationResponse(revision, entry), FeederMonitorMutationResponse.serializer(), status)

    private fun <T> ok(body: T, serializer: kotlinx.serialization.KSerializer<T>, status: Int = 200) = MockResponse()
        .setResponseCode(status)
        .setHeader("Content-Type", "application/json")
        .setBody(json.encodeToString(serializer, body))

    companion object {
        const val LIST_PATH = "/api/v1/feedermonitor"
        const val EVENTS_PATH = "/api/v1/feedermonitor/events"
        const val FEEDERS_PATH = "/api/v1/feedermonitor/feeders/"
        private const val ZERO_UUID = "00000000-0000-0000-0000-000000000000"
        private val UUID_FORMAT = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

        fun problem(status: Int, code: String, retryAfter: Long? = null): MockResponse = MockResponse()
            .setResponseCode(status)
            .setHeader("Content-Type", "application/problem+json")
            .apply { retryAfter?.let { setHeader("Retry-After", it.toString()) } }
            .setBody("""{"type":"about:blank","title":"x","status":$status,"detail":"detail","code":"$code"}""")

        /** The connection drops after the request was read, the client cannot tell whether it was applied. */
        fun dropped(): MockResponse = MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
    }
}
