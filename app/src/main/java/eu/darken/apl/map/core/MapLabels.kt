package eu.darken.apl.map.core

enum class MapLabelField { CALLSIGN, TYPE, ALTITUDE, SPEED }

/** What an aircraft's map label shows. With no field on, only the selected aircraft gets a label. */
data class MapLabelFields(
    val callsign: Boolean = false,
    val type: Boolean = false,
    val altitude: Boolean = false,
    val speed: Boolean = false,
) {

    fun isEnabled(field: MapLabelField): Boolean = when (field) {
        MapLabelField.CALLSIGN -> callsign
        MapLabelField.TYPE -> type
        MapLabelField.ALTITUDE -> altitude
        MapLabelField.SPEED -> speed
    }

    fun with(field: MapLabelField, enabled: Boolean): MapLabelFields = when (field) {
        MapLabelField.CALLSIGN -> copy(callsign = enabled)
        MapLabelField.TYPE -> copy(type = enabled)
        MapLabelField.ALTITUDE -> copy(altitude = enabled)
        MapLabelField.SPEED -> copy(speed = enabled)
    }

    /** The label text changes while an aircraft stays in view. */
    val hasMotion: Boolean
        get() = altitude || speed

    val hasAny: Boolean
        get() = MapLabelField.entries.any { isEnabled(it) }
}

class MapLabelText(
    private val fields: MapLabelFields,
    private val altitudeText: (feet: Int) -> String,
    private val groundText: String,
    private val speedText: (knots: Int) -> String,
) {

    fun label(plane: MapPlane, selected: Boolean): String {
        if (!fields.hasAny) return if (selected) plane.callsign ?: plane.hex else ""
        return lines(plane, selected)
    }

    private fun lines(plane: MapPlane, selected: Boolean): String = buildList {
        if (fields.callsign) add(plane.callsign ?: plane.hex)
        if (fields.type) plane.aircraftType?.takeIf { it.isNotBlank() }?.let { add(it) }
        motionLine(plane, selected)?.let { add(it) }
    }.joinToString("\n")

    private fun motionLine(plane: MapPlane, selected: Boolean): String? {
        // Slow aircraft on the ground crowd airports, they only get this line once selected
        val stationary = plane.onGround && (plane.groundSpeedKnots == null || plane.groundSpeedKnots <= GROUND_MOVING_KNOTS)
        if (stationary && !selected) return null

        val altitude = when {
            !fields.altitude -> null
            plane.onGround -> groundText
            else -> plane.altitudeFt?.let(altitudeText)
        }
        val speed = if (fields.speed) plane.groundSpeedKnots?.let(speedText) else null
        return listOfNotNull(altitude, speed).joinToString("  ").ifEmpty { null }
    }

    companion object {
        private const val GROUND_MOVING_KNOTS = 18
    }
}
