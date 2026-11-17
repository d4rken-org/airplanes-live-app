package eu.darken.apl.main.core.aircraft

import androidx.annotation.StringRes
import eu.darken.apl.R

enum class SquawkRegion(@StringRes val labelRes: Int) {
    EUROPE(R.string.aircraft_squawk_region_europe),
    UNITED_STATES(R.string.aircraft_squawk_region_united_states),
    UNITED_KINGDOM(R.string.aircraft_squawk_region_united_kingdom),
    CANADA(R.string.aircraft_squawk_region_canada),
}

data class SquawkMeaning(
    val code: SquawkCode,
    /** Empty means worldwide. */
    val regions: Set<SquawkRegion>,
    @StringRes val textRes: Int,
)

object SquawkMeanings {

    private val ALL = listOf(
        SquawkMeaning("7500", emptySet(), R.string.aircraft_squawk_meaning_7500),
        SquawkMeaning("7600", emptySet(), R.string.aircraft_squawk_meaning_7600),
        SquawkMeaning("7700", emptySet(), R.string.aircraft_squawk_meaning_7700),
        SquawkMeaning(
            "7400",
            setOf(SquawkRegion.UNITED_STATES, SquawkRegion.UNITED_KINGDOM),
            R.string.aircraft_squawk_meaning_7400,
        ),
        SquawkMeaning("2000", setOf(SquawkRegion.EUROPE), R.string.aircraft_squawk_meaning_2000_europe),
        SquawkMeaning("2000", setOf(SquawkRegion.CANADA), R.string.aircraft_squawk_meaning_2000_canada),
        SquawkMeaning("1000", setOf(SquawkRegion.EUROPE), R.string.aircraft_squawk_meaning_1000_europe),
        SquawkMeaning("1000", setOf(SquawkRegion.UNITED_STATES), R.string.aircraft_squawk_meaning_1000_us),
        SquawkMeaning("1000", setOf(SquawkRegion.CANADA), R.string.aircraft_squawk_meaning_1000_canada),
        SquawkMeaning("7000", setOf(SquawkRegion.EUROPE), R.string.aircraft_squawk_meaning_7000_europe),
        SquawkMeaning("1200", setOf(SquawkRegion.UNITED_STATES), R.string.aircraft_squawk_meaning_1200_us),
        SquawkMeaning("1200", setOf(SquawkRegion.CANADA), R.string.aircraft_squawk_meaning_1200_canada),
    )

    fun of(squawk: String?): List<SquawkMeaning> {
        val code = squawk?.trim()?.takeIf { it.isNotEmpty() } ?: return emptyList()
        return ALL.filter { it.code == code }
    }
}
