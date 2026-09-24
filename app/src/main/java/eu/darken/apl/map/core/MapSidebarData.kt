package eu.darken.apl.map.core

import androidx.annotation.StringRes
import eu.darken.apl.R

data class MapSidebarData(
    val totalAircraft: Int,
    val onScreen: Int,
    val aircraft: List<SidebarAircraft>,
) {
    data class SidebarAircraft(
        val hex: String,
        val callsign: String?,
        val icaoType: String?,
        val country: String?,
        val altitude: String?,
        val speed: String?,
        val altitudeNumeric: Int? = when {
            altitude == null -> null
            altitude.equals("ground", ignoreCase = true) -> 0
            else -> altitude.filter { it.isDigit() || it == '-' }.toIntOrNull()
        },
        val speedNumeric: Int? = speed?.filter { it.isDigit() }?.toIntOrNull(),
    )

    enum class SortField(@StringRes val labelRes: Int) {
        CALLSIGN(R.string.map_sidebar_sort_callsign),
        TYPE(R.string.map_sidebar_sort_type),
        ALTITUDE(R.string.map_sidebar_sort_altitude),
        SPEED(R.string.map_sidebar_sort_speed),
    }

    /** No [field] keeps the given order. */
    fun sortedBy(field: SortField?, ascending: Boolean): MapSidebarData {
        if (field == null) return this
        val comparator = when (field) {
            SortField.CALLSIGN -> compareBy<SidebarAircraft> { it.callsign ?: it.hex }
            SortField.TYPE -> compareBy { it.icaoType ?: "" }
            SortField.ALTITUDE -> compareBy { it.altitudeNumeric }
            SortField.SPEED -> compareBy { it.speedNumeric }
        }
        return copy(aircraft = aircraft.sortedWith(if (ascending) comparator else comparator.reversed()))
    }
}
