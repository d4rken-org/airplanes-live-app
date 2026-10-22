package eu.darken.apl.map.core

import eu.darken.apl.server.api.HistoryHourStatus
import eu.darken.apl.server.api.HistoryRunSpan
import eu.darken.apl.server.api.TrailPoint
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * One aircraft's [track] laid out in [zone]'s local days. Times are epoch ms; only `[from, until)`
 * of the track counts, its neighbours outside the range just shape the edges that cross a bound.
 */
class HistoryTimeline(
    val track: AircraftHistoryTracks.Track,
    val zone: ZoneId,
) {

    /**
     * The straight line between two consecutive kept points [a] and [b], cut to [start]..[end]. Its
     * colour comes from [b], like the live trail's.
     */
    data class Edge(val a: TrailPoint, val b: TrailPoint, val start: Long, val end: Long) {
        val startPoint: TrailPoint get() = pointAt(start)
        val endPoint: TrailPoint get() = pointAt(end)

        /** The middle of [b]'s altitude band, null where its altitude is unknown. */
        val bandFeet: Double? get() = b.altitudeFeet?.let { (floor(it / ALTITUDE_BAND_FEET) + 0.5) * ALTITUDE_BAND_FEET }

        fun pointAt(time: Long): TrailPoint {
            val f = ((time - a.observedAt).toDouble() / (b.observedAt - a.observedAt)).coerceIn(0.0, 1.0)
            val dLon = normalize(b.longitude - a.longitude)
            val altitude = if (a.altitudeFeet != null && b.altitudeFeet != null) {
                a.altitudeFeet + (b.altitudeFeet - a.altitudeFeet) * f
            } else {
                null
            }
            return TrailPoint(
                latitude = a.latitude + (b.latitude - a.latitude) * f,
                longitude = normalize(a.longitude + dLon * f),
                altitudeFeet = altitude,
                observedAt = time,
            )
        }
    }

    /** Positions without a gap from [start] to [end], labelled with the callsign it mostly carried. */
    data class Stretch(val start: Long, val end: Long, val callsign: String?)

    data class Span(val start: Long, val end: Long, val kind: SpanKind)

    /** Drawn in this order, later kinds over earlier ones. */
    enum class SpanKind {
        /** Some minutes of the hour were not recorded. */
        PARTIAL,

        /** Nothing of the hour was recorded. */
        NOT_RECORDED,

        /** Recorded, but dropped on this device to stay within the point cap. */
        OMITTED,

        /** Before the kept days or after the time loaded. */
        OUTSIDE,
    }

    data class Day(
        val date: LocalDate,
        /** The whole local day, 23 or 25 hours long on clock changes. */
        val start: Long,
        val end: Long,
        val edges: List<Edge>,
        /** Single sightings, nothing joins them to a line. */
        val sightings: List<TrailPoint>,
        val stretches: List<Stretch>,
        val spans: List<Span>,
        /** Uncut and with their own times, they are only drawn: playback and positions skip them. */
        val gaps: List<TrailGap> = emptyList(),
    ) {
        val isEmpty: Boolean get() = stretches.isEmpty() && sightings.isEmpty()
    }

    data class Position(
        val point: TrailPoint,
        /** Null for a single sighting, which has no direction. */
        val trackDegrees: Float?,
        /** Estimated from the two kept points around it. */
        val speedKnots: Int?,
    )

    /** Where playback is after a step; [holdMs] of real time to rest on a single sighting. */
    data class Step(val cursor: Long, val playing: Boolean, val holdMs: Long = 0)

    private val from = track.from
    private val until = track.until

    private val edges: List<Edge>
    private val sightings: List<TrailPoint>
    private val stretches: List<Stretch>

    init {
        val edges = mutableListOf<Edge>()
        val sightings = mutableListOf<TrailPoint>()
        val stretches = mutableListOf<Stretch>()
        var component = mutableListOf<TrailPoint>()
        fun flush() {
            if (component.size == 1) {
                component.single().takeIf { it.observedAt in from until until }?.let { sightings.add(it) }
            } else if (component.size > 1) {
                val start = component.first().observedAt.coerceAtLeast(from)
                val end = component.last().observedAt.coerceAtMost(until)
                if (end > start) stretches.add(Stretch(start, end, callsignOver(start, end)))
            }
            component = mutableListOf()
        }
        track.points.forEach { point ->
            val previous = component.lastOrNull()
            if (previous != null && point.observedAt - previous.observedAt > track.maxGapMs) flush()
            if (previous != null && point.observedAt > previous.observedAt && point.observedAt - previous.observedAt <= track.maxGapMs) {
                val start = previous.observedAt.coerceAtLeast(from)
                val end = point.observedAt.coerceAtMost(until)
                if (end > start) edges.add(Edge(previous, point, start, end))
            }
            if (previous == null || point.observedAt != previous.observedAt) component.add(point)
        }
        flush()
        this.edges = edges
        this.sightings = sightings
        this.stretches = stretches
    }

    // A gap between two different known callsigns is two flights, not one dropping out of reception
    private val gaps: List<TrailGap> = TrailGaps.find(firstPerTime(track.points), track.maxGapMs) { gap ->
        val before = callsignAt(gap.from.observedAt)
            ?: callsignOver(gap.from.observedAt - track.maxGapMs, gap.from.observedAt)
        val after = callsignAt(gap.to.observedAt)
            ?: callsignOver(gap.to.observedAt, gap.to.observedAt + track.maxGapMs)
        before == null || after == null || before == after
    }.filter { it.to.observedAt > from && it.from.observedAt < until }

    /** Every local day `[from, until)` touches, oldest first. */
    val days: List<LocalDate> = if (until <= from) {
        emptyList()
    } else {
        val first = localDate(from)
        val last = localDate(until - 1)
        generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.toList()
    }

    private val dayCache = mutableMapOf<LocalDate, Day>()

    @Synchronized
    fun day(date: LocalDate): Day = dayCache.getOrPut(date) { buildDay(date) }

    private fun buildDay(date: LocalDate): Day {
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEdges = edges.mapNotNull { edge ->
            val s = maxOf(edge.start, start)
            val e = minOf(edge.end, end)
            if (e > s) edge.copy(start = s, end = e) else null
        }
        val daySightings = sightings.filter { it.observedAt in start until end }
        val dayStretches = stretches.mapNotNull { stretch ->
            val s = maxOf(stretch.start, start)
            val e = minOf(stretch.end, end)
            if (e > s) stretch.copy(start = s, end = e) else null
        }
        val dayGaps = gaps.filter { it.to.observedAt > start && it.from.observedAt < end }
        return Day(date, start, end, dayEdges, daySightings, dayStretches, spans(start, end), dayGaps)
    }

    private fun spans(start: Long, end: Long): List<Span> = buildList {
        fun add(s: Long, e: Long, kind: SpanKind) {
            val cs = maxOf(s, start)
            val ce = minOf(e, end)
            if (ce > cs) add(Span(cs, ce, kind))
        }
        track.hours.forEach { hour ->
            val kind = when (hour.coverage) {
                HistoryHourStatus.COVERAGE_NONE -> SpanKind.NOT_RECORDED
                HistoryHourStatus.COVERAGE_PARTIAL -> SpanKind.PARTIAL
                else -> return@forEach
            }
            add(maxOf(hour.hour, from), minOf(hour.hour + HOUR_MS, until), kind)
        }
        track.omittedUntil?.let { add(from, it, SpanKind.OMITTED) }
        add(start, from, SpanKind.OUTSIDE)
        add(until, end, SpanKind.OUTSIDE)
    }.sortedBy { it.kind }

    fun localDate(time: Long): LocalDate = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()

    /** The start of the newest stretch or sighting, null without any. */
    fun latestStart(): Long? = maxOfOrNull(stretches.lastOrNull()?.start, sightings.lastOrNull()?.observedAt)

    fun callsignAt(time: Long): String? = track.runs
        .lastOrNull { it.kind == HistoryRunSpan.KIND_CALLSIGN && time in it.startAt..it.lastSeenAt }
        ?.value?.trim()?.takeIf { it.isNotEmpty() }

    /** Null where the aircraft was not seen; a sighting shows for [SIGHTING_SHOW_MS] around its time. */
    fun positionAt(time: Long): Position? {
        edgeAt(time)?.let { edge ->
            val seconds = (edge.b.observedAt - edge.a.observedAt) / 1000.0
            return Position(
                point = edge.pointAt(time),
                trackDegrees = bearing(edge.a, edge.b),
                speedKnots = (distanceNm(edge.a, edge.b) / seconds * 3600).roundToInt().takeIf { seconds > 0 },
            )
        }
        val sighting = sightings.minByOrNull { abs(it.observedAt - time) }
            ?.takeIf { abs(it.observedAt - time) <= SIGHTING_SHOW_MS }
            ?: return null
        return Position(point = sighting, trackDegrees = null, speedKnots = null)
    }

    private fun edgeAt(time: Long): Edge? {
        var low = 0
        var high = edges.lastIndex
        while (low <= high) {
            val mid = (low + high) ushr 1
            val edge = edges[mid]
            when {
                time < edge.start -> high = mid - 1
                time > edge.end -> low = mid + 1
                else -> return edge
            }
        }
        return null
    }

    /**
     * Plays [day] on by [deltaMs] from [cursor]: gaps take no time, a single sighting on the way is
     * stopped at for [SIGHTING_HOLD_MS], and playback ends where the day's last positions end.
     */
    fun advance(day: Day, cursor: Long, deltaMs: Long): Step {
        var time = cursor
        var remaining = deltaMs
        while (true) {
            val stretch = day.stretches.firstOrNull { time >= it.start && time < it.end }
            if (stretch != null) {
                if (time + remaining < stretch.end) return Step(time + remaining, playing = true)
                remaining -= stretch.end - time
                time = stretch.end
                continue
            }
            val nextStretch = day.stretches.firstOrNull { it.start > time }
            val nextSighting = day.sightings.firstOrNull { it.observedAt > time }
            when {
                nextSighting != null && (nextStretch == null || nextSighting.observedAt < nextStretch.start) ->
                    return Step(nextSighting.observedAt, playing = true, holdMs = SIGHTING_HOLD_MS)

                nextStretch != null -> time = nextStretch.start
                // A stretch into the next day ends at its midnight, which already belongs to that day
                else -> return Step(minOf(time, day.end - 1), playing = false)
            }
        }
    }

    /** Where Play starts: [cursor], or the day's first positions when nothing is left after it. */
    fun playFrom(day: Day, cursor: Long): Long {
        val ahead = day.stretches.any { minOf(it.end, day.end - 1) > cursor } || day.sightings.any { it.observedAt > cursor }
        if (ahead) return cursor
        return minOfOrNull(day.stretches.firstOrNull()?.start, day.sightings.firstOrNull()?.observedAt) ?: cursor
    }

    private fun callsignOver(start: Long, end: Long): String? = track.runs
        .filter { it.kind == HistoryRunSpan.KIND_CALLSIGN }
        .map { run -> run to (minOf(run.lastSeenAt, end) - maxOf(run.startAt, start)) }
        .filter { (_, overlap) -> overlap >= 0 }
        .maxByOrNull { (_, overlap) -> overlap }
        ?.first?.value?.trim()?.takeIf { it.isNotEmpty() }

    companion object {
        const val ALTITUDE_BAND_FEET = 2_000.0
        const val SIGHTING_SHOW_MS = 60_000L
        const val SIGHTING_HOLD_MS = 1_000L
        private const val HOUR_MS = 3_600_000L
        private const val EARTH_RADIUS_NM = 3_440.065

        /** Of points sharing a time only the first, like the edges keep. */
        private fun firstPerTime(points: List<TrailPoint>): List<TrailPoint> =
            points.filterIndexed { i, point -> i == 0 || point.observedAt != points[i - 1].observedAt }

        private fun maxOfOrNull(a: Long?, b: Long?): Long? = if (a == null) b else if (b == null) a else maxOf(a, b)
        private fun minOfOrNull(a: Long?, b: Long?): Long? = if (a == null) b else if (b == null) a else minOf(a, b)

        /** Into -180..180, so lines across the antimeridian take the short way. */
        internal fun normalize(longitude: Double): Double {
            var value = longitude
            while (value > 180.0) value -= 360.0
            while (value < -180.0) value += 360.0
            return value
        }

        private fun bearing(a: TrailPoint, b: TrailPoint): Float {
            val lat1 = Math.toRadians(a.latitude)
            val lat2 = Math.toRadians(b.latitude)
            val dLon = Math.toRadians(normalize(b.longitude - a.longitude))
            val y = sin(dLon) * cos(lat2)
            val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
            return ((Math.toDegrees(atan2(y, x)) + 360.0) % 360.0).toFloat()
        }

        private fun distanceNm(a: TrailPoint, b: TrailPoint): Double {
            val lat1 = Math.toRadians(a.latitude)
            val lat2 = Math.toRadians(b.latitude)
            val dLat = lat2 - lat1
            val dLon = Math.toRadians(normalize(b.longitude - a.longitude))
            val h = sin(dLat / 2).let { it * it } + cos(lat1) * cos(lat2) * sin(dLon / 2).let { it * it }
            return 2 * EARTH_RADIUS_NM * asin(sqrt(h.coerceIn(0.0, 1.0)))
        }
    }
}
