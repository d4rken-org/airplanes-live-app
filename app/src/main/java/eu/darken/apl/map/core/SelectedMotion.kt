package eu.darken.apl.map.core

/**
 * The selected aircraft between frames, for drawing it on every display frame: [delayMs] in the past,
 * between the two frames around that moment, so it moves smoothly without running ahead of the newest.
 */
class SelectedMotion(private val delayMs: Long = DELAY_MS) {

    private class Sample(val at: Long, val plane: MapPlane)

    private val samples = ArrayDeque<Sample>()

    /** [at] is when [plane] was placed, on the clock whose time is later passed to [planeAt]. */
    fun add(at: Long, plane: MapPlane) {
        val last = samples.lastOrNull()
        when {
            last == null -> {}
            last.plane.hex != plane.hex -> samples.clear()
            // A second frame of the same moment knows at least as much as the first
            at <= last.at -> samples.removeLast()
        }
        samples.addLast(Sample(at, plane))
        while (samples.size > MAX_SAMPLES) samples.removeFirst()
    }

    fun clear() = samples.clear()

    fun planeAt(now: Double): MapPlane? {
        if (samples.isEmpty()) return null
        val shownAt = now - delayMs
        val next = samples.indexOfFirst { it.at > shownAt }
        return when (next) {
            // Frames stopped coming, the newest stays until they resume
            -1 -> samples.last().plane
            // Just selected, nothing yet to move between
            0 -> samples.first().plane
            else -> {
                val from = samples[next - 1]
                val to = samples[next]
                between(from.plane, to.plane, ((shownAt - from.at) / (to.at - from.at)).toFloat())
            }
        }
    }

    companion object {
        const val DELAY_MS = 150L
        private const val MAX_SAMPLES = 8

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
