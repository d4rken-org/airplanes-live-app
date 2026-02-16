package eu.darken.apl.search.ui

import eu.darken.apl.common.navigation.NavigationDestination
import kotlinx.serialization.Serializable

@Serializable
data class DestinationSearch(
    val targetHexes: List<String>? = null,
    val targetSquawks: List<String>? = null,
    val targetCallsigns: List<String>? = null,
) : NavigationDestination
