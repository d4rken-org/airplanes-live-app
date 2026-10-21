package eu.darken.apl.common.units

import eu.darken.apl.R
import eu.darken.apl.common.preferences.EnumPreference
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Units for what an aircraft reports about itself: altitude, speed, vertical rate and temperature. */
@Serializable
enum class AircraftUnits(override val labelRes: Int) : EnumPreference<AircraftUnits> {
    @SerialName("AVIATION") AVIATION(R.string.units_aircraft_aviation_label),
    @SerialName("METRIC") METRIC(R.string.units_aircraft_metric_label),
    @SerialName("IMPERIAL") IMPERIAL(R.string.units_aircraft_imperial_label),
    ;
}
