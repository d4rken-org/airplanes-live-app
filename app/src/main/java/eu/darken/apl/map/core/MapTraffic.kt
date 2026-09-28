package eu.darken.apl.map.core

import eu.darken.apl.ar.core.ScreenProjection
import eu.darken.apl.main.core.aircraft.AircraftHex
import eu.darken.apl.server.api.MapAircraft
import kotlin.math.roundToInt

/** One aircraft as drawn in one frame. */
data class MapPlane(
    val hex: AircraftHex,
    val latitude: Double,
    val longitude: Double,
    val trackDegrees: Float?,
    val altitudeFt: Int?,
    val groundSpeedKnots: Int?,
    val onGround: Boolean,
    val callsign: String?,
    val aircraftType: String?,
    val category: String?,
    val military: Boolean,
    /** Past the extrapolation limit the plane holds still and fades until it is hidden. */
    val opacity: Float,
)

/**
 * The aircraft of the newest map answer. Each answer is complete for the area it was asked for, so
 * it replaces the previous membership instead of adding to it.
 */
class MapTraffic {

    var aircraft: Map<AircraftHex, MapAircraft> = emptyMap()
        private set

    fun replace(answer: List<MapAircraft>) {
        aircraft = answer.associateBy { it.id.uppercase() }
    }

    fun plane(hex: AircraftHex, nowMillis: Long): MapPlane? = aircraft[hex]?.let { place(hex, it, nowMillis) }

    fun planes(nowMillis: Long): List<MapPlane> = aircraft.mapNotNull { (hex, ac) -> place(hex, ac, nowMillis) }

    /**
     * The opacity of those of [hexes] that are fading by now, 0 once they are past hiding. Unlike a
     * position this needs no new [planes], so aircraft can fade between rebuilds.
     */
    fun fades(hexes: Collection<AircraftHex>, nowMillis: Long): Map<AircraftHex, Float> = buildMap {
        hexes.forEach { hex ->
            val observedAt = aircraft[hex]?.position?.observedAt ?: return@forEach
            val ageSec = ((nowMillis - observedAt) / 1000f).coerceAtLeast(0f)
            val opacity = if (ageSec >= HIDE_AGE_SEC) 0f else opacityFor(ageSec)
            if (opacity < 1f) put(hex, opacity)
        }
    }

    companion object {
        const val EXTRAPOLATE_AGE_SEC = 15f
        const val HIDE_AGE_SEC = 60f
        private const val MIN_OPACITY = 0.3f

        /**
         * Without a position time the observation cannot be placed in time. Downloading an old
         * position again does not make it fresher, the age always comes from [nowMillis].
         */
        internal fun place(hex: AircraftHex, ac: MapAircraft, nowMillis: Long): MapPlane? {
            val observedAt = ac.position.observedAt ?: return null
            // A position from slightly ahead of the local server time estimate counts as current
            val ageSec = ((nowMillis - observedAt) / 1000f).coerceAtLeast(0f)
            if (ageSec >= HIDE_AGE_SEC) return null

            val track = ac.trackDegrees?.toFloat()
            val speed = ac.groundSpeedKnots?.toFloat()
            val canExtrapolate = ac.onGround != true &&
                    track != null && speed != null &&
                    track.isFinite() && speed.isFinite() &&
                    track in 0f..360f && speed in 0f..2000f

            val (lat, lon) = if (canExtrapolate) {
                ScreenProjection.extrapolatePosition(
                    ac.position.latitude,
                    ac.position.longitude,
                    track!!,
                    speed!!,
                    ageSec.coerceAtMost(EXTRAPOLATE_AGE_SEC),
                )
            } else {
                ac.position.latitude to ac.position.longitude
            }

            return MapPlane(
                hex = hex,
                latitude = lat,
                longitude = MapViewport.normalizeLongitude(lon),
                trackDegrees = track?.takeIf { it.isFinite() },
                altitudeFt = ac.altitudeFeet?.roundToInt(),
                groundSpeedKnots = speed?.takeIf { it.isFinite() }?.roundToInt(),
                onGround = ac.onGround == true,
                callsign = ac.callsign?.trim()?.takeIf { it.isNotEmpty() },
                aircraftType = ac.aircraftType,
                category = ac.category,
                military = ac.military,
                opacity = opacityFor(ageSec),
            )
        }

        // In steps, so a fade only produces an update when it is visible
        private fun opacityFor(ageSec: Float): Float = when {
            ageSec <= EXTRAPOLATE_AGE_SEC -> 1f
            else -> {
                val progress = (ageSec - EXTRAPOLATE_AGE_SEC) / (HIDE_AGE_SEC - EXTRAPOLATE_AGE_SEC)
                val opacity = (1f - progress * (1f - MIN_OPACITY)).coerceIn(MIN_OPACITY, 1f)
                (opacity * OPACITY_STEPS).roundToInt() / OPACITY_STEPS
            }
        }

        private const val OPACITY_STEPS = 20f
    }
}
