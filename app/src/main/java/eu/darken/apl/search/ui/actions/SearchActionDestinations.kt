package eu.darken.apl.search.ui.actions

import eu.darken.apl.common.navigation.NavigationDestination
import kotlinx.serialization.Serializable

@Serializable
data class DestinationSearchAction(
    val hex: String,
) : NavigationDestination
