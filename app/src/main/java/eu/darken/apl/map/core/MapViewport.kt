package eu.darken.apl.map.core

/**
 * A geographic rectangle as the map shows it. [west] > [east] means the rectangle crosses the
 * antimeridian, e.g. west 170, east -170 covers 20 degrees around it. [zoom] is on tar1090's scale,
 * like [MapOptions.Camera].
 */
data class MapViewport(
    val south: Double,
    val north: Double,
    val west: Double,
    val east: Double,
    val zoom: Double,
) {

    val isGlobal: Boolean
        get() = west == -180.0 && east == 180.0

    private val lonSpan: Double
        get() = if (west <= east) east - west else east - west + 360.0

    /** In square degrees, only for comparing rectangles at similar latitudes. */
    val area: Double
        get() = (north - south) * lonSpan

    /**
     * The area to ask the server for: [fraction] of each side's span added around the visible
     * area, so small pans stay inside what was fetched.
     */
    fun padded(fraction: Double = PADDING): MapViewport {
        val latPad = (north - south) * fraction
        val paddedSouth = (south - latPad).coerceAtLeast(-MAX_LAT)
        val paddedNorth = (north + latPad).coerceAtMost(MAX_LAT)

        val span = lonSpan
        val paddedSpan = span * (1 + 2 * fraction)
        if (paddedSpan >= 360.0) return copy(south = paddedSouth, north = paddedNorth, west = -180.0, east = 180.0)

        val lonPad = span * fraction
        return copy(
            south = paddedSouth,
            north = paddedNorth,
            west = normalizeLongitude(west - lonPad),
            east = normalizeLongitude(east + lonPad),
        )
    }

    operator fun contains(other: MapViewport): Boolean {
        if (other.south < south || other.north > north) return false
        if (isGlobal) return true
        if (other.isGlobal) return false
        // Measured eastwards from this west edge, so crossing the antimeridian needs no special case
        val otherWest = eastwardOffset(other.west)
        val otherEast = otherWest + other.lonSpan
        return otherEast <= lonSpan
    }

    fun containsPoint(latitude: Double, longitude: Double): Boolean {
        if (latitude < south || latitude > north) return false
        if (isGlobal) return true
        return eastwardOffset(normalizeLongitude(longitude)) <= lonSpan
    }

    private fun eastwardOffset(longitude: Double): Double = (longitude - west + 360.0) % 360.0

    companion object {
        const val PADDING = 0.25

        /** Beyond this, web mercator maps stop, and so does what they can show. */
        const val MAX_LAT = 85.0511

        /**
         * MapLibre reports longitudes beyond ±180 after panning across the antimeridian; the server
         * expects them within.
         */
        fun normalizeLongitude(longitude: Double): Double {
            // Wrapping costs precision, a longitude that needs none is returned as it is
            if (longitude in -180.0..180.0) return longitude
            val wrapped = ((longitude + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
            // Keeps 180 as 180 instead of folding it onto -180
            return if (wrapped == -180.0 && longitude > 0) 180.0 else wrapped
        }

        /** From an unwrapped visible region, where east - west is the real span even beyond ±180. */
        fun fromVisibleRegion(south: Double, north: Double, west: Double, east: Double, zoom: Double): MapViewport {
            val clampedSouth = south.coerceIn(-MAX_LAT, MAX_LAT)
            val clampedNorth = north.coerceIn(-MAX_LAT, MAX_LAT)
            if (east - west >= 360.0) return MapViewport(clampedSouth, clampedNorth, -180.0, 180.0, zoom)
            return MapViewport(clampedSouth, clampedNorth, normalizeLongitude(west), normalizeLongitude(east), zoom)
        }
    }
}
