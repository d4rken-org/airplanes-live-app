package eu.darken.apl.map.core

enum class MapLabelField { CALLSIGN, TYPE, ALTITUDE, SPEED }

/** What an aircraft's map label shows. At least one field stays enabled. */
data class MapLabelFields(
    val callsign: Boolean = true,
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

    fun canDisable(field: MapLabelField): Boolean = MapLabelField.entries.any { it != field && isEnabled(it) }

    private val isEmpty: Boolean
        get() = MapLabelField.entries.none { isEnabled(it) }

    companion object {
        /** A combination without any field shows the defaults instead of nothing. */
        fun orDefaults(fields: MapLabelFields): MapLabelFields = if (fields.isEmpty) MapLabelFields() else fields
    }
}

class MapLabelText(
    private val fields: MapLabelFields,
    private val altitudeText: (feet: Int) -> String,
    private val groundText: String,
    private val speedText: (knots: Int) -> String,
) {

    fun label(plane: MapPlane, selected: Boolean): String = buildList {
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
