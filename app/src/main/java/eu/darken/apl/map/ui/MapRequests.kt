package eu.darken.apl.map.ui

import eu.darken.apl.common.navigation.TabRequest
import eu.darken.apl.map.core.MapOptions
import javax.inject.Inject
import javax.inject.Singleton

/** What other screens asked the map to show. */
@Singleton
class MapRequests @Inject constructor() : TabRequest<MapOptions>()
