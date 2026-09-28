package eu.darken.apl.feeder.ui

import eu.darken.apl.common.navigation.NavigationDestination
import eu.darken.apl.common.navigation.OverlayDestination
import kotlinx.serialization.Serializable

@Serializable
data object DestinationFeederList : NavigationDestination

@Serializable
data class DestinationFeederAction(
    val receiverId: String,
) : OverlayDestination

@Serializable
data class DestinationAddFeeder(
    val qrData: String? = null,
    val receiverId: String? = null,
) : NavigationDestination

/** Route class kept for restored back-stack compatibility, it opens the upgrade screen. */
@Serializable
data object DestinationLinkFeeder : NavigationDestination
