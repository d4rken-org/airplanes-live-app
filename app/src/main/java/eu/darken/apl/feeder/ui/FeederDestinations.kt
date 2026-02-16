package eu.darken.apl.feeder.ui

import eu.darken.apl.common.navigation.NavigationDestination
import kotlinx.serialization.Serializable

@Serializable
data object DestinationFeederList : NavigationDestination

@Serializable
data class DestinationFeederAction(
    val receiverId: String,
) : NavigationDestination

@Serializable
data class DestinationAddFeeder(
    val qrData: String? = null,
) : NavigationDestination
