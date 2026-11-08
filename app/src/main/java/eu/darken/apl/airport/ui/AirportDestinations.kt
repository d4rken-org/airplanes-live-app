package eu.darken.apl.airport.ui

import eu.darken.apl.airport.core.Airport
import eu.darken.apl.common.navigation.NavigationDestination
import kotlinx.serialization.Serializable

/** Carries the whole airport, so the sheet comes back from a saved back stack without asking the server. */
@Serializable
data class DestinationAirportDetails(
    val airport: Airport,
) : NavigationDestination
