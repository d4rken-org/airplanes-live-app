package eu.darken.apl.map.ui

import eu.darken.apl.map.core.MapAircraftProvider
import eu.darken.apl.map.core.MapLabelText

/**
 * For the map's collector of (frame, label text) pairs. Other label text, e.g. other fields or units,
 * comes as the frame already drawn once more: its labels have to be written again, but its aircraft
 * must not be moved again.
 */
internal class MapLabelRefresh {
    private var frame: MapAircraftProvider.Frame? = null
    private var labels: MapLabelText? = null

    data class Step(
        /** False when the pair repeats the frame already drawn. */
        val newFrame: Boolean,
        /** The traffic and the selected aircraft carry labels from other label text. */
        val relabel: Boolean,
    )

    fun next(frame: MapAircraftProvider.Frame, labels: MapLabelText): Step {
        val step = Step(newFrame = frame !== this.frame, relabel = labels !== this.labels)
        this.frame = frame
        this.labels = labels
        return step
    }
}
