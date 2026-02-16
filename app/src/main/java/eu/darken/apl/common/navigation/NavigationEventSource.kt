package eu.darken.apl.common.navigation

import eu.darken.apl.common.flow.SingleEventFlow

interface NavigationEventSource {
    val navEvents: SingleEventFlow<NavEvent>
}
