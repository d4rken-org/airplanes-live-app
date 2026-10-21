package eu.darken.apl.common.units

import eu.darken.apl.R
import eu.darken.apl.common.preferences.EnumPreference
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class DistanceUnit(val meters: Double) {
    KILOMETERS(1_000.0),
    NAUTICAL_MILES(1_852.0),
    MILES(1_609.344),
    ;
}

@Serializable
enum class DistanceUnitSetting(override val labelRes: Int) : EnumPreference<DistanceUnitSetting> {
    @SerialName("AUTOMATIC") AUTOMATIC(R.string.units_distance_automatic_label),
    @SerialName("KILOMETERS") KILOMETERS(R.string.units_distance_kilometers_label),
    @SerialName("NAUTICAL_MILES") NAUTICAL_MILES(R.string.units_distance_nautical_miles_label),
    @SerialName("MILES") MILES(R.string.units_distance_miles_label),
    ;

    /** [region] is an ISO 3166 country code, e.g. "US" gives miles and "DE" kilometers. */
    fun resolve(region: String?): DistanceUnit = when (this) {
        AUTOMATIC -> if (region?.uppercase() in MILES_REGIONS) DistanceUnit.MILES else DistanceUnit.KILOMETERS
        KILOMETERS -> DistanceUnit.KILOMETERS
        NAUTICAL_MILES -> DistanceUnit.NAUTICAL_MILES
        MILES -> DistanceUnit.MILES
    }

    companion object {
        // Where road distances are signed in miles
        private val MILES_REGIONS = setOf("US", "GB", "LR", "MM")
    }
}
