package eu.darken.apl.map.core

import eu.darken.apl.server.api.TrailPoint
import kotlin.math.floor

/**
 * What the map draws of an aircraft's history: lines that keep one colour each, and the single
 * sightings no line reaches.
 */
data class HistoryRender(
    val lines: List<Line>,
    val dots: List<TrailPoint>,
) {
    /** [altitudeFeet] is the middle of the line's altitude band, null where the altitude is unknown. */
    data class Line(
        val latitudes: List<Double>,
        val longitudes: List<Double>,
        val altitudeFeet: Double?,
    )

    companion object {
        const val ALTITUDE_BAND_FEET = 2_000.0

        /**
         * [points] ascending by time. Only points up to [until] are drawn, the live trail takes over
         * from there. Like the live trail, an edge has the altitude of the point it leads to;
         * consecutive edges of one band become one line, which ends where the next one starts.
         */
        fun build(points: List<TrailPoint>, maxGapMs: Long, until: Long? = null): HistoryRender {
            val kept = if (until == null) points else points.filter { it.observedAt <= until }
            val lines = mutableListOf<Line>()
            val dots = mutableListOf<TrailPoint>()

            var component = mutableListOf<TrailPoint>()
            fun flush() {
                when (component.size) {
                    0 -> {}
                    1 -> dots.add(component.single())
                    else -> lines.addAll(bandLines(component))
                }
                component = mutableListOf()
            }
            kept.forEach { point ->
                val previous = component.lastOrNull()
                if (previous != null && point.observedAt - previous.observedAt > maxGapMs) flush()
                component.add(point)
            }
            flush()
            return HistoryRender(lines, dots)
        }

        private fun bandLines(component: List<TrailPoint>): List<Line> {
            val lines = mutableListOf<Line>()
            var start = 0
            var band = bandOf(component[1].altitudeFeet)
            for (index in 2 until component.size) {
                val next = bandOf(component[index].altitudeFeet)
                if (next != band) {
                    lines.add(line(component.subList(start, index), band))
                    start = index - 1
                    band = next
                }
            }
            lines.add(line(component.subList(start, component.size), band))
            return lines
        }

        /** Null for an unknown altitude, which is its own band. */
        private fun bandOf(altitudeFeet: Double?): Int? = altitudeFeet?.let { floor(it / ALTITUDE_BAND_FEET).toInt() }

        private fun line(points: List<TrailPoint>, band: Int?) = Line(
            latitudes = points.map { it.latitude },
            longitudes = points.map { it.longitude },
            altitudeFeet = band?.let { (it + 0.5) * ALTITUDE_BAND_FEET },
        )
    }
}
