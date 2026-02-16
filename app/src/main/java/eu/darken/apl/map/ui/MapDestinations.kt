package eu.darken.apl.map.ui

import eu.darken.apl.common.navigation.NavigationDestination
import eu.darken.apl.map.core.MapOptions
import kotlinx.serialization.Serializable

@Serializable
data class DestinationMap(
    val mapOptions: MapOptions? = null,
) : NavigationDestination
