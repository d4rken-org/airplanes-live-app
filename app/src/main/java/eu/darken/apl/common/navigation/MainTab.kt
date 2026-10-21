package eu.darken.apl.common.navigation

import eu.darken.apl.feeder.ui.DestinationFeederList
import eu.darken.apl.map.ui.DestinationMap
import eu.darken.apl.search.ui.DestinationSearch
import eu.darken.apl.watch.ui.DestinationWatchList

enum class MainTab(val root: NavigationDestination) {
    MAP(DestinationMap()),
    SEARCH(DestinationSearch()),
    WATCH(DestinationWatchList()),
    FEEDER(DestinationFeederList),
    ;

    companion object {
        /** The tab whose own screen [destination] is, null for screens opened on top of a tab. */
        fun of(destination: NavigationDestination): MainTab? = when (destination) {
            is DestinationMap -> MAP
            is DestinationSearch -> SEARCH
            is DestinationWatchList -> WATCH
            DestinationFeederList -> FEEDER
            else -> null
        }
    }
}
