package eu.darken.apl.map.ui

import eu.darken.apl.map.core.MapLabelFields

/** Tells from MapLibre's camera events whether a finger is moving the map. */
internal class MapGesture {
    private var byFinger = false

    var active = false
        private set

    fun onMoveStarted(gesture: Boolean) {
        byFinger = gesture
        if (gesture) active = true
    }

    fun onCameraChanged() {
        if (byFinger) active = true
    }

    /**
     * The camera has not changed for a while. A finger may still rest on the map and move on,
     * which [onCameraChanged] then picks up.
     */
    fun onSettled() {
        active = false
    }

    fun onIdle() {
        byFinger = false
        active = false
    }
}

/**
 * Whether MapLibre fades labels in and out as it places them.
 *
 * MapLibre matches a label across source updates by its text. One whose altitude or speed changed counts as new
 * and stays hidden until the next placement pass, which the fade throttles to one per 300 ms. Without the fade every
 * frame places, which costs while a finger moves the map, so the fade stays on for that long
 * (MapLibre Native android-v13.6.1).
 */
internal fun labelFade(labels: Boolean, fields: MapLabelFields, gesturing: Boolean): Boolean =
    gesturing || !(labels && fields.hasMotion)
