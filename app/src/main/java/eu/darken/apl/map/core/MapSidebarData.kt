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
        val onGround: Boolean = false,
        val altitudeFt: Int? = null,
        val speedKt: Int? = null,
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
            // Aircraft on the ground sort as the lowest
            SortField.ALTITUDE -> compareBy { if (it.onGround) 0 else it.altitudeFt }
            SortField.SPEED -> compareBy { it.speedKt }
        }
        return copy(aircraft = aircraft.sortedWith(if (ascending) comparator else comparator.reversed()))
    }
}
