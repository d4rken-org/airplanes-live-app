package eu.darken.apl.map.core

import eu.darken.apl.ar.core.ScreenProjection
import eu.darken.apl.main.core.aircraft.AircraftHex
import eu.darken.apl.server.api.MapAircraft
import kotlin.math.abs

/**
 * The selected aircraft, placed for any moment by the same rules as the map's frames, so it can be
 * drawn on every display frame. A new answer changes where it is predicted to be; instead of jumping
 * there, the drawing crosses from the old prediction to the new one over [blendMs]. Both are moving
 * predictions and the crossing starts and ends gently, so position and speed stay continuous.
 */
class SelectedMotion(private val blendMs: Long = BLEND_MS) {

    /** Placed at the server time `elapsed + serverOffset`. */
    private class Model(val hex: AircraftHex, val aircraft: MapAircraft, val serverOffset: Long) {
        fun at(elapsed: Double): MapPlane? = MapTraffic.place(hex, aircraft, (elapsed + serverOffset).toLong())

        fun movesLike(other: Model): Boolean = hex == other.hex &&
                abs(serverOffset - other.serverOffset) <= CLOCK_TOLERANCE_MS &&
                aircraft.position == other.aircraft.position &&
                aircraft.trackDegrees == other.aircraft.trackDegrees &&
                aircraft.groundSpeedKnots == other.aircraft.groundSpeedKnots &&
                aircraft.onGround == other.aircraft.onGround
    }

    private sealed interface Path {
        val target: Model
        val depth: Int
    }

    private class Fixed(override val target: Model) : Path {
        override val depth get() = 0
    }

    private class Blend(val from: Path, override val target: Model, val start: Double) : Path {
        override val depth get() = from.depth + 1
    }

    private var path: Path? = null

    /** The newest answer while too many crossings are under way, taken up once one has finished. */
    private var waiting: Model? = null

    /**
     * [now] is when the drawing takes this up, so the crossing starts where the aircraft is drawn.
     * [serverOffset] maps [now]'s clock to server time.
     */
    fun update(now: Double, hex: AircraftHex, aircraft: MapAircraft, serverOffset: Long) {
        val next = Model(hex, aircraft, serverOffset)
        val current = path?.pruned(now)
        val crossing = current != null && current.target.hex == hex && !current.target.movesLike(next)
        if (crossing && current!!.depth >= MAX_CROSSINGS) {
            waiting = next
            path = current
            return
        }
        waiting = null
        path = adopt(now, current, next)
    }

    fun clear() {
        path = null
        waiting = null
    }

    fun planeAt(now: Double): MapPlane? {
        var current = path?.pruned(now) ?: return null
        val next = waiting
        if (next != null && current.depth < MAX_CROSSINGS) {
            current = adopt(now, current, next)
            waiting = null
        }
        path = current
        return current.planeAt(now)
    }

    private fun adopt(now: Double, current: Path?, next: Model): Path = when {
        current == null || current.target.hex != next.hex -> Fixed(next)
        // New details of the same motion, e.g. a callsign, need no crossing
        current.target.movesLike(next) -> current.retargeted(Model(next.hex, next.aircraft, current.target.serverOffset))
        else -> {
            val drawn = current.planeAt(now)
            val predicted = next.at(now)
            val far = drawn != null && predicted != null && ScreenProjection.haversineDistanceM(
                drawn.latitude, drawn.longitude, predicted.latitude, predicted.longitude,
            ) > SNAP_DISTANCE_M
            if (drawn == null || predicted == null || far) Fixed(next) else Blend(current, next, now)
        }
    }

    private fun Path.pruned(now: Double): Path = when (this) {
        is Fixed -> this
        is Blend -> if (now >= start + blendMs) {
            Fixed(target)
        } else {
            val inner = from.pruned(now)
            if (inner === from) this else Blend(inner, target, start)
        }
    }

    private fun Path.retargeted(model: Model): Path = when (this) {
        is Fixed -> Fixed(model)
        is Blend -> Blend(from, model, start)
    }

    private fun Path.planeAt(now: Double): MapPlane? = when (this) {
        is Fixed -> target.at(now)
        is Blend -> {
            val to = target.at(now)
            val from = from.planeAt(now)
            when {
                // The newest answer is too old to show, whatever came before
                to == null -> null
                from == null -> to
                else -> {
                    val progress = ((now - start) / blendMs).coerceIn(0.0, 1.0).toFloat()
                    between(from, to, progress * progress * (3f - 2f * progress))
                }
            }
        }
    }

    companion object {
        const val BLEND_MS = 1_000L

        /** Beyond this the old prediction was wrong enough that sliding over would look stranger than a jump. */
        private const val SNAP_DISTANCE_M = 1_000.0
        private const val MAX_CROSSINGS = 8

        /** Each answer anchors the server time anew, off by however long the network took. */
        private const val CLOCK_TOLERANCE_MS = 50L

        internal fun between(from: MapPlane, to: MapPlane, fraction: Float): MapPlane {
            // The short way round, across the antimeridian if that is shorter
            var lonDelta = to.longitude - from.longitude
            if (lonDelta > 180.0) lonDelta -= 360.0
            if (lonDelta < -180.0) lonDelta += 360.0
            val fromTrack = from.trackDegrees
            val toTrack = to.trackDegrees
            val track = if (fromTrack != null && toTrack != null) {
                // 350° to 10° turns through north, not back across south
                val turn = ((toTrack - fromTrack + 540f) % 360f) - 180f
                (fromTrack + turn * fraction + 360f) % 360f
            } else {
                toTrack
            }
            return to.copy(
                latitude = from.latitude + (to.latitude - from.latitude) * fraction,
                longitude = MapViewport.normalizeLongitude(from.longitude + lonDelta * fraction),
                trackDegrees = track,
            )
        }
    }
}
