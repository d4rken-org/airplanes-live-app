package eu.darken.apl.map.core

import androidx.annotation.StringRes
import eu.darken.apl.R
import eu.darken.apl.common.geo.Spherical
import eu.darken.apl.main.core.aircraft.AircraftHex
import eu.darken.apl.main.core.aircraft.IcaoCountries

data class MapSidebarData(
    /** Aircraft inside the visible map, after the map's own filters. */
    val inView: Int,
    /** Those of [inView] that pass the list's own filter, sorted, none left out. */
    val matches: List<SidebarAircraft>,
    val mapFilter: MapFilter?,
    /** The map answer left aircraft out, so some in view can be missing. */
    val serverLimited: Boolean,
    val controls: Controls,
) {
    data class SidebarAircraft(
        val hex: AircraftHex,
        val callsign: String?,
        val icaoType: String?,
        val countryCode: String?,
        val altitudeFt: Int?,
        val onGround: Boolean,
        val speedKnots: Int?,
        val military: Boolean,
        val shape: AircraftShapes.Ref?,
        val watched: Boolean,
        /** Only while sorted by [SortField.DISTANCE]. */
        val distanceMeters: Double?,
    )

    enum class MapFilter { MILITARY_ONLY, ISOLATED }

    data class Controls(
        val query: String = "",
        val watchedOnly: Boolean = false,
        val sort: SortField = SortField.CALLSIGN,
        val ascending: Boolean = true,
    ) {
        val isFiltering: Boolean get() = query.isNotBlank() || watchedOnly
    }

    enum class SortField(@StringRes val labelRes: Int) {
        CALLSIGN(R.string.map_sidebar_sort_callsign),
        TYPE(R.string.map_sidebar_sort_type),
        ALTITUDE(R.string.map_sidebar_sort_altitude),
        SPEED(R.string.map_sidebar_sort_speed),
        DISTANCE(R.string.common_distance_label),
    }

    companion object {
        /** The list shows this many of the [matches] at most. */
        const val ROW_LIMIT = 200

        fun build(
            planes: List<MapPlane>,
            controls: Controls,
            watched: Set<AircraftHex>,
            /** Latitude and longitude [SortField.DISTANCE] measures from, null while unknown. */
            center: Pair<Double, Double>?,
            shapes: AircraftShapes?,
            mapFilter: MapFilter?,
            serverLimited: Boolean,
        ): MapSidebarData {
            val query = controls.query.trim()
            val matches = planes
                .asSequence()
                .filter { !controls.watchedOnly || it.hex in watched }
                .filter { query.isEmpty() || it.matches(query) }
                .map { plane ->
                    SidebarAircraft(
                        hex = plane.hex,
                        callsign = plane.callsign?.trim()?.takeIf { it.isNotEmpty() },
                        icaoType = plane.aircraftType,
                        countryCode = IcaoCountries.countryCode(plane.hex),
                        altitudeFt = plane.altitudeFt,
                        onGround = plane.onGround,
                        speedKnots = plane.groundSpeedKnots,
                        military = plane.military,
                        shape = shapes?.refFor(plane.aircraftType, plane.category),
                        watched = plane.hex in watched,
                        distanceMeters = center?.takeIf { controls.sort == SortField.DISTANCE }?.let { (lat, lon) ->
                            Spherical.haversineDistanceM(lat, lon, plane.latitude, plane.longitude)
                        },
                    )
                }
                .sortedWith(comparator(controls.sort, controls.ascending))
                .toList()
            return MapSidebarData(
                inView = planes.size,
                matches = matches,
                mapFilter = mapFilter,
                serverLimited = serverLimited,
                controls = controls,
            )
        }

        private fun MapPlane.matches(query: String): Boolean =
            callsign?.contains(query, ignoreCase = true) == true ||
                    aircraftType?.contains(query, ignoreCase = true) == true ||
                    hex.contains(query, ignoreCase = true)

        /** Aircraft without the value come last in either direction, ties go by hex so rows don't swap places. */
        private fun comparator(field: SortField, ascending: Boolean): Comparator<SidebarAircraft> {
            val byField: Comparator<SidebarAircraft> = when (field) {
                SortField.CALLSIGN -> nullsLast(ascending) { it.callsign?.uppercase() ?: it.hex.uppercase() }
                SortField.TYPE -> nullsLast(ascending) { it.icaoType?.uppercase() }
                // On the ground is below any altitude, also below airfields under sea level
                SortField.ALTITUDE -> nullsLast(ascending) { if (it.onGround) Int.MIN_VALUE else it.altitudeFt }
                SortField.SPEED -> nullsLast(ascending) { it.speedKnots }
                SortField.DISTANCE -> nullsLast(ascending) { it.distanceMeters }
            }
            return byField.thenBy { it.hex }
        }

        private fun <T : Comparable<T>> nullsLast(
            ascending: Boolean,
            key: (SidebarAircraft) -> T?,
        ): Comparator<SidebarAircraft> = Comparator { a, b ->
            val ka = key(a)
            val kb = key(b)
            when {
                ka == null && kb == null -> 0
                ka == null -> 1
                kb == null -> -1
                ascending -> ka.compareTo(kb)
                else -> kb.compareTo(ka)
            }
        }
    }
}
