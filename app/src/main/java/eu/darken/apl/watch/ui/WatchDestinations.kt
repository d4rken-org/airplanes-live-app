package eu.darken.apl.watch.ui

import eu.darken.apl.common.navigation.NavigationDestination
import kotlinx.serialization.Serializable

@Serializable
data class DestinationWatchList(
    val targetAircraft: List<String>? = null,
) : NavigationDestination

@Serializable
data class DestinationWatchDetails(
    val watchId: String,
) : NavigationDestination

@Serializable
data class DestinationCreateAircraftWatch(
    val hex: String? = null,
    val note: String? = null,
) : NavigationDestination

@Serializable
data class DestinationCreateFlightWatch(
    val callsign: String? = null,
    val note: String? = null,
) : NavigationDestination

@Serializable
data class DestinationCreateSquawkWatch(
    val squawk: String? = null,
    val note: String? = null,
) : NavigationDestination
