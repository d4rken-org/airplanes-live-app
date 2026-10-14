package eu.darken.apl.main.core.aircraft

import androidx.annotation.StringRes
import eu.darken.apl.R

enum class SquawkEmergency(val code: SquawkCode, @StringRes val meaningRes: Int) {
    HIJACK("7500", R.string.map_aircraft_details_squawk_7500),
    RADIO_FAILURE("7600", R.string.map_aircraft_details_squawk_7600),
    EMERGENCY("7700", R.string.map_aircraft_details_squawk_7700);

    companion object {
        fun fromSquawk(squawk: String?): SquawkEmergency? =
            squawk?.trim()?.let { trimmed -> entries.find { it.code == trimmed } }
    }
}
