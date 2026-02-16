package eu.darken.apl.main.ui

import eu.darken.apl.common.navigation.NavigationDestination
import kotlinx.serialization.Serializable

@Serializable
data object DestinationMain : NavigationDestination

@Serializable
data object DestinationWelcome : NavigationDestination

@Serializable
data object DestinationPrivacy : NavigationDestination
