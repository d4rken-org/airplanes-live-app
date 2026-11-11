package eu.darken.apl.airport.ui

import eu.darken.apl.airport.core.AirportTarget
import eu.darken.apl.common.navigation.NavigationDestination
import kotlinx.serialization.Serializable

/** A [AirportTarget.Full] comes back from a saved back stack without asking the server. */
@Serializable
data class DestinationAirportDetails(
    val target: AirportTarget,
) : NavigationDestination
