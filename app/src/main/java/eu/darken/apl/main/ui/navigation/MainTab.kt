package eu.darken.apl.main.ui.navigation

import eu.darken.apl.common.navigation.NavTab
import eu.darken.apl.common.navigation.NavigationDestination
import eu.darken.apl.feeder.ui.DestinationFeederList
import eu.darken.apl.map.ui.DestinationMap
import eu.darken.apl.search.ui.DestinationSearch
import eu.darken.apl.watch.ui.DestinationWatchList

enum class MainTab(override val root: NavigationDestination) : NavTab {
    MAP(DestinationMap()),
    SEARCH(DestinationSearch()),
    WATCH(DestinationWatchList()),
    FEEDER(DestinationFeederList),
    ;

    override val id: String
        get() = name
}
