package eu.darken.apl.common.flight

import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.main.core.aircraft.AircraftHex
import eu.darken.apl.main.core.aircraft.Callsign
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

/** One emission per hex and callsign, an aircraft source re-emits on every position update. */
fun Flow<Aircraft?>.routeLookupKeys(): Flow<Pair<AircraftHex, Callsign?>> = filterNotNull()
    .map { it.hex to it.callsign?.trim()?.uppercase()?.takeIf { c -> c.isNotEmpty() } }
    .distinctUntilChanged()
