package eu.darken.apl.search.core

import android.location.Location
import eu.darken.apl.main.core.aircraft.AircraftHex
import eu.darken.apl.main.core.aircraft.Airframe
import eu.darken.apl.main.core.aircraft.Callsign
import eu.darken.apl.main.core.aircraft.Registration
import eu.darken.apl.main.core.aircraft.SquawkCode

data class SearchBuckets(
    val hexes: Set<AircraftHex> = emptySet(),
    val callsigns: Set<Callsign> = emptySet(),
    val registrations: Set<Registration> = emptySet(),
    val squawks: Set<SquawkCode> = emptySet(),
    val airframes: Set<Airframe> = emptySet(),
    val military: Boolean = false,
    val ladd: Boolean = false,
    val pia: Boolean = false,
    val location: Location? = null,
    val locationRange: Long = 0,
) {
    val isCacheSupported: Boolean
        get() = !military && !ladd && !pia && location == null
}

fun SearchQuery.toBuckets(): SearchBuckets = when (this) {
    is SearchQuery.All -> {
        val hexes = mutableSetOf<AircraftHex>()
        val callsigns = mutableSetOf<Callsign>()
        val registrations = mutableSetOf<Registration>()
        val squawks = mutableSetOf<SquawkCode>()
        val airframes = mutableSetOf<Airframe>()
        terms.filter { it.isNotBlank() }.forEach { term ->
            when {
                term.length == 4 && term.all { it.isDigit() } -> squawks.add(term)
                term.length == 6 && term.all { it.isDigit() || it.uppercaseChar() in 'A'..'F' } -> hexes.add(term)
                term.length <= 4 -> airframes.add(term)
                term.length in 5..8 && term.any { it.isDigit() } -> registrations.add(term)
                term.length in 5..8 -> callsigns.add(term)
                else -> callsigns.add(term)
            }
        }
        SearchBuckets(
            hexes = hexes,
            callsigns = callsigns,
            registrations = registrations,
            squawks = squawks,
            airframes = airframes,
        )
    }

    is SearchQuery.Hex -> SearchBuckets(hexes = hexes)
    is SearchQuery.Callsign -> SearchBuckets(callsigns = signs)
    is SearchQuery.Registration -> SearchBuckets(registrations = regs)
    is SearchQuery.Squawk -> SearchBuckets(squawks = codes)
    is SearchQuery.Airframe -> SearchBuckets(airframes = types)
    is SearchQuery.Interesting -> SearchBuckets(military = military, ladd = ladd, pia = pia)
    is SearchQuery.Position -> SearchBuckets(location = location, locationRange = rangeInMeter)
}

sealed interface SearchQuery {

    val isEmpty: Boolean

    data class All(val terms: Set<String> = emptySet()) : SearchQuery {
        override val isEmpty: Boolean
            get() = terms.isEmpty()
    }

    data class Hex(val hexes: Set<AircraftHex> = emptySet()) : SearchQuery {
        constructor(hex: AircraftHex) : this(setOf(hex))

        override val isEmpty: Boolean
            get() = hexes.isEmpty()
    }

    data class Callsign(val signs: Set<eu.darken.apl.main.core.aircraft.Callsign> = emptySet()) : SearchQuery {

        constructor(callsign: eu.darken.apl.main.core.aircraft.Callsign) : this(setOf(callsign))

        override val isEmpty: Boolean
            get() = signs.isEmpty()
    }

    data class Registration(val regs: Set<eu.darken.apl.main.core.aircraft.Registration> = emptySet()) : SearchQuery {
        override val isEmpty: Boolean
            get() = regs.isEmpty()
    }

    data class Squawk(val codes: Set<SquawkCode> = emptySet()) : SearchQuery {

        constructor(code: SquawkCode) : this(setOf(code))

        override val isEmpty: Boolean
            get() = codes.isEmpty()
    }

    data class Airframe(val types: Set<eu.darken.apl.main.core.aircraft.Airframe> = emptySet()) : SearchQuery {
        override val isEmpty: Boolean
            get() = types.isEmpty()
    }

    data class Interesting(
        val military: Boolean = false,
        val ladd: Boolean = false,
        val pia: Boolean = false,
    ) : SearchQuery {
        override val isEmpty: Boolean
            get() = !military && !ladd && !pia
    }

    data class Position(
        val location: Location = Location("empty"),
        val rangeInMeter: Long = 185200L,
    ) : SearchQuery {
        override val isEmpty: Boolean
            get() = location.provider == "empty"
    }
}