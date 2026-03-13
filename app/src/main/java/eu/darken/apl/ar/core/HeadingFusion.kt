package eu.darken.apl.ar.core

import kotlin.math.exp

class HeadingFusion(private val tau: Float = 5f) {

    var smoothedOffsetRad: Float = 0f
        private set
    private var initialized: Boolean = false
    private var lastTimestampNs: Long = 0

    fun update(gameAzimuthRad: Float, magAzimuthRad: Float, timestampNs: Long): Float {
        val rawOffset = circularDelta(magAzimuthRad, gameAzimuthRad)

        if (!initialized) {
            smoothedOffsetRad = rawOffset
            lastTimestampNs = timestampNs
            initialized = true
            return smoothedOffsetRad
        }

        val dt = (timestampNs - lastTimestampNs) / 1e9f
        lastTimestampNs = timestampNs

        if (dt <= 0f || dt > 2f) return smoothedOffsetRad

        val alpha = 1f - exp(-dt / tau)
        smoothedOffsetRad += alpha * circularDelta(rawOffset, smoothedOffsetRad)
        smoothedOffsetRad = wrapToPi(smoothedOffsetRad)

        return smoothedOffsetRad
    }

    fun reset() {
        initialized = false
        smoothedOffsetRad = 0f
        lastTimestampNs = 0
    }

    companion object {
        fun circularDelta(a: Float, b: Float): Float = wrapToPi(a - b)

        private fun wrapToPi(angle: Float): Float {
            var result = angle
            while (result > Math.PI.toFloat()) result -= (2 * Math.PI).toFloat()
            while (result < -Math.PI.toFloat()) result += (2 * Math.PI).toFloat()
            return result
        }
    }
}
