package eu.darken.apl.ar.core

import eu.darken.apl.ar.ui.ArLabel
import eu.darken.apl.main.core.aircraft.AircraftHex
import java.util.concurrent.atomic.AtomicInteger

class ArLabelSmoother {

    private val prevPositions = mutableMapOf<AircraftHex, Pair<Float, Float>>()
    private val pendingResetRotation = AtomicInteger(NO_PENDING_RESET)

    fun smooth(labels: List<ArLabel>, frameRotation: Int?): List<ArLabel> {
        // Frames projected for the old rotation can still arrive after a turn; reset on the first new one
        val pending = pendingResetRotation.get()
        if (pending != NO_PENDING_RESET && pending == frameRotation && pendingResetRotation.compareAndSet(pending, NO_PENDING_RESET)) {
            prevPositions.clear()
        }
        val currentHexes = mutableSetOf<AircraftHex>()
        val result = labels.map { label ->
            currentHexes.add(label.hex)
            val prev = prevPositions[label.hex]
            if (prev != null) {
                val smoothX = prev.first + LABEL_ALPHA * (label.screenXNorm - prev.first)
                val smoothY = prev.second + LABEL_ALPHA * (label.screenYNorm - prev.second)
                prevPositions[label.hex] = smoothX to smoothY
                label.copy(screenXNorm = smoothX, screenYNorm = smoothY)
            } else {
                prevPositions[label.hex] = label.screenXNorm to label.screenYNorm
                label
            }
        }
        prevPositions.keys.retainAll(currentHexes)
        return result
    }

    fun requestReset(forRotation: Int) {
        pendingResetRotation.set(forRotation)
    }

    companion object {
        internal const val LABEL_ALPHA = 0.4f
        private const val NO_PENDING_RESET = -1
    }
}
