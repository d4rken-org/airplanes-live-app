package eu.darken.apl.map.core

import androidx.annotation.StringRes
import eu.darken.apl.R
import org.json.JSONObject

data class MapSidebarData(
    val totalAircraft: Int,
    val onScreen: Int,
    val aircraft: List<SidebarAircraft>,
) {
    data class SidebarAircraft(
        val hex: String,
        val callsign: String?,
        val icaoType: String?,
        val squawk: String?,
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
        SQUAWK(R.string.map_sidebar_sort_squawk),
    }

    companion object {
        fun fromJson(json: String): MapSidebarData? {
            val obj = try {
                JSONObject(json)
            } catch (_: Exception) {
                return null
            }

            val totalAircraft = obj.optInt("totalAircraft", 0)
            val onScreen = obj.optInt("onScreen", 0)

            val jsonArray = obj.optJSONArray("aircraft")
                ?: return MapSidebarData(totalAircraft, onScreen, emptyList())
            val aircraft = mutableListOf<SidebarAircraft>()
            for (i in 0 until jsonArray.length()) {
                val ac = jsonArray.optJSONObject(i) ?: continue
                val hex = ac.optString("hex").takeIf { it.isNotBlank() } ?: continue
                aircraft.add(
                    SidebarAircraft(
                        hex = hex,
                        callsign = ac.optString("callsign").takeIf { it.isNotBlank() },
                        icaoType = ac.optString("icaoType").takeIf { it.isNotBlank() },
                        squawk = ac.optString("squawk").takeIf { it.isNotBlank() },
                        country = ac.optString("country").takeIf { it.isNotBlank() },
                        altitude = ac.optString("altitude").takeIf { it.isNotBlank() },
                        speed = ac.optString("speed").takeIf { it.isNotBlank() },
                    )
                )
            }

            return MapSidebarData(
                totalAircraft = totalAircraft,
                onScreen = onScreen,
                aircraft = aircraft,
            )
        }
    }
}
