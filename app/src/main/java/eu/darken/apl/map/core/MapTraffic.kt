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
    /** Only known when it matches a squawk watch, not to be shown as the aircraft's squawk. */
    val squawk: String?,
    val aircraftType: String?,
    val category: String?,
    val military: Boolean,
    /** Past the extrapolation limit the plane holds still and fades until it is hidden. */
    val opacity: Float,
    val kind: AircraftKind = AircraftKind.UNKNOWN,
    val headingDegrees: Float? = null,
    /** Which way the icon points, see [MapTraffic.bearing]; null draws it pointing north. */
    val bearingDegrees: Float? = null,
)

/** The list's search, [query] trimmed and not blank: "dlh" finds DLH453, "a32" an A320, "3c6" 3C65A3. */
internal fun MapPlane.matchesQuery(query: String): Boolean =
    callsign?.contains(query, ignoreCase = true) == true ||
            aircraftType?.contains(query, ignoreCase = true) == true ||
            hex.contains(query, ignoreCase = true)

/**
 * The aircraft of the newest map answer. Each answer is complete for the area it was asked for, so
 * it replaces the previous membership instead of adding to it.
 */
class MapTraffic {

    var aircraft: Map<AircraftHex, MapAircraft> = emptyMap()
        private set

    private class Remembered(val bearing: Float, val answeredAt: Long)

    /**
     * The last direction of aircraft that stopped reporting one, e.g. parked at a gate, so they don't
     * turn north. Forgotten [REMEMBER_MS] after an aircraft was last in an answer.
     */
    private val remembered = HashMap<AircraftHex, Remembered>()

    /** [serverTime] is when the server answered. */
    fun replace(answer: List<MapAircraft>, serverTime: Long) {
        aircraft = answer.associateBy { it.id.uppercase() }
        remembered.values.removeAll { serverTime - it.answeredAt > REMEMBER_MS }
        aircraft.forEach { (hex, ac) ->
            val bearing = bearingOf(ac) ?: remembered[hex]?.bearing ?: return@forEach
            remembered[hex] = Remembered(bearing, serverTime)
        }
    }

    /** Until the next answer nothing is drawn. Remembered directions stay, [replace] expires them. */
    fun clear() {
        aircraft = emptyMap()
    }

    /** What [hex] is drawn by while it reports no direction of its own. */
    fun rememberedBearing(hex: AircraftHex): Float? = remembered[hex]?.bearing

    fun plane(hex: AircraftHex, nowMillis: Long): MapPlane? =
        aircraft[hex]?.let { place(hex, it, nowMillis, rememberedBearing(hex)) }

    fun planes(nowMillis: Long): List<MapPlane> =
        aircraft.mapNotNull { (hex, ac) -> place(hex, ac, nowMillis, rememberedBearing(hex)) }

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
        internal const val REMEMBER_MS = 10 * 60 * 1000L

        /**
         * On the ground the heading is where the nose points, also when pushed back, and most aircraft
         * there report no track. In the air the track is the way the map shows it moving.
         */
        internal fun bearing(onGround: Boolean, trackDegrees: Number?, headingDegrees: Number?): Float? {
            val track = trackDegrees.direction()
            val heading = headingDegrees.direction()
            return if (onGround) heading ?: track else track ?: heading
        }

        internal fun bearingOf(ac: MapAircraft): Float? = bearing(ac.onGround == true, ac.trackDegrees, ac.trueHeadingDegrees)

        private fun Number?.direction(): Float? = this?.toFloat()?.takeIf { it.isFinite() && it in 0f..360f }

        /**
         * Without a position time the observation cannot be placed in time. Downloading an old
         * position again does not make it fresher, the age always comes from [nowMillis].
         * [rememberedBearing] points the icon while the aircraft reports no direction.
         */
        internal fun place(hex: AircraftHex, ac: MapAircraft, nowMillis: Long, rememberedBearing: Float? = null): MapPlane? {
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
                squawk = ac.squawk?.trim()?.takeIf { it.isNotEmpty() },
                aircraftType = ac.aircraftType,
                category = ac.category,
                military = ac.military,
                opacity = opacityFor(ageSec),
                kind = AircraftKind.fromWire(ac.kind),
                headingDegrees = ac.trueHeadingDegrees?.toFloat()?.takeIf { it.isFinite() },
                bearingDegrees = bearingOf(ac) ?: rememberedBearing,
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
