package eu.darken.apl.main.ui.navigation

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import eu.darken.apl.common.navigation.NavTab
import eu.darken.apl.common.navigation.NavigationDestination
import eu.darken.apl.common.navigation.TabSetup
import eu.darken.apl.feeder.ui.DestinationFeederList
import eu.darken.apl.main.ui.DestinationMain
import eu.darken.apl.map.ui.DestinationMap
import eu.darken.apl.map.ui.MapRequests
import eu.darken.apl.search.ui.DestinationSearch
import eu.darken.apl.search.ui.SearchRequests
import eu.darken.apl.search.ui.SearchTarget
import eu.darken.apl.watch.ui.DestinationWatchList
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MainTabSetup @Inject constructor(
    private val mapRequests: MapRequests,
    private val searchRequests: SearchRequests,
) : TabSetup {

    override val tabs: List<NavTab> = MainTab.entries

    override val startTab: NavTab = MainTab.MAP

    override val bootstrapRoot: NavigationDestination = DestinationMain

    override fun tabOf(destination: NavigationDestination): NavTab? = when (destination) {
        is DestinationMap -> MainTab.MAP
        is DestinationSearch -> MainTab.SEARCH
        is DestinationWatchList -> MainTab.WATCH
        DestinationFeederList -> MainTab.FEEDER
        else -> null
    }

    override fun deliver(destination: NavigationDestination) {
        when (destination) {
            is DestinationMap -> destination.mapOptions?.let { mapRequests.publish(it) }
            is DestinationSearch -> if (destination != MainTab.SEARCH.root) {
                searchRequests.publish(
                    SearchTarget(
                        hexes = destination.targetHexes,
                        squawks = destination.targetSquawks,
                        callsigns = destination.targetCallsigns,
                    )
                )
            }
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class TabSetupModule {
    @Binds
    abstract fun tabSetup(setup: MainTabSetup): TabSetup
}
