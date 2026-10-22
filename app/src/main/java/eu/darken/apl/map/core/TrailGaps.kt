package eu.darken.apl.map.core

import eu.darken.apl.common.geo.Spherical
import eu.darken.apl.server.api.TrailPoint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.time.Duration.Companion.hours

/**
 * Where the aircraft was lost, [from], and found again, [to]. Drawn as a dashed line that joins the
 * two, the way it flew in between is unknown.
 */
data class TrailGap(val from: TrailPoint, val to: TrailPoint) {

    /** The great circle from [from] to [to], a point about every [PATH_STEP_M], as latitude to longitude. */
    fun path(): List<Pair<Double, Double>> {
        val distance = Spherical.haversineDistanceM(from.latitude, from.longitude, to.latitude, to.longitude)
        val steps = ceil(distance / PATH_STEP_M).toInt().coerceAtLeast(1)
        // The ends exactly, so the line meets the trail on both sides
        val between = (1 until steps).map { step ->
            Spherical.intermediate(from.latitude, from.longitude, to.latitude, to.longitude, step.toDouble() / steps)
        }
        return listOf(from.latitude to from.longitude) + between + (to.latitude to to.longitude)
    }

    companion object {
        /** Straight lines on the map stray visibly from the great circle only over hundreds of km. */
        const val PATH_STEP_M = 50_000.0
    }
}

object TrailGaps {

    /**
     * Gaps longer than [maxGapMs] that look like one flight dropping out of reception: at most
     * [MAX_GAP_DURATION] long, flying at [MIN_SPEED_KNOTS] or more on both sides and on average
     * across, and heading on both sides within [MAX_TURN_DEGREES] of the line between them. An
     * aircraft that flew out and came back the opposite way is left with no line. [allow] can rule
     * out more.
     *
     * [points] ascending.
     */
    fun find(points: List<TrailPoint>, maxGapMs: Long, allow: (TrailGap) -> Boolean = { true }): List<TrailGap> {
        val gaps = mutableListOf<TrailGap>()
        for (i in 0 until points.lastIndex) {
            val from = points[i]
            val to = points[i + 1]
            val duration = to.observedAt - from.observedAt
            if (duration <= maxGapMs || duration > MAX_GAP_DURATION_MS) continue
            val length = Spherical.haversineDistanceM(from.latitude, from.longitude, to.latitude, to.longitude)
            if (length < MIN_LENGTH_M) continue
            // Slower is a stop out of sight, like landing and taking off again the same way. Faster than
            // any aircraft is a bad position, and keeps out ends so far apart the great circle is ambiguous
            val knots = length / (duration / 1000.0) / METERS_PER_SECOND_PER_KNOT
            if (knots < MIN_SPEED_KNOTS || knots > MAX_SPEED_KNOTS) continue
            val before = points.subList(0, i).lastOrNull { it.observedAt < from.observedAt } ?: continue
            val after = points.subList(i + 2, points.size).firstOrNull { it.observedAt > to.observedAt } ?: continue
            if (!flying(before, from, maxGapMs) || !flying(to, after, maxGapMs)) continue
            // Each direction is compared where it applies: arriving at from, leaving to
            val lost = bearing(from, before) + PI
            val found = bearing(to, after)
            if (turn(lost, bearing(from, to)) > MAX_TURN_RAD || turn(found, bearing(to, from) + PI) > MAX_TURN_RAD) continue
            val gap = TrailGap(from, to)
            if (allow(gap)) gaps.add(gap)
        }
        return gaps
    }

    private fun flying(earlier: TrailPoint, later: TrailPoint, maxGapMs: Long): Boolean {
        val ms = later.observedAt - earlier.observedAt
        if (ms > maxGapMs) return false
        val meters = Spherical.haversineDistanceM(earlier.latitude, earlier.longitude, later.latitude, later.longitude)
        return meters / (ms / 1000.0) >= MIN_SPEED_KNOTS * METERS_PER_SECOND_PER_KNOT
    }

    private fun bearing(a: TrailPoint, b: TrailPoint) = Spherical.bearingRad(a.latitude, a.longitude, b.latitude, b.longitude)

    /** The smaller angle between two bearings, 0..π. */
    private fun turn(a: Double, b: Double): Double {
        val diff = abs(a - b) % (2 * PI)
        return if (diff > PI) 2 * PI - diff else diff
    }

    val MAX_GAP_DURATION = 6.hours
    private val MAX_GAP_DURATION_MS = MAX_GAP_DURATION.inWholeMilliseconds

    /** Faster than any taxiing, so parked and taxiing aircraft get no line. */
    const val MIN_SPEED_KNOTS = 50.0
    const val MAX_SPEED_KNOTS = 1_200.0
    const val MAX_TURN_DEGREES = 90.0
    private val MAX_TURN_RAD = Math.toRadians(MAX_TURN_DEGREES)
    private const val MIN_LENGTH_M = 100.0
    private const val METERS_PER_SECOND_PER_KNOT = 1852.0 / 3600.0
}
