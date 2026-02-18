package eu.darken.apl.watch.ui

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.apl.common.navigation.BottomSheetSceneStrategy
import eu.darken.apl.common.navigation.NavigationEntry
import eu.darken.apl.watch.ui.create.CreateAircraftWatchDialogHost
import eu.darken.apl.watch.ui.create.CreateFlightWatchDialogHost
import eu.darken.apl.watch.ui.create.CreateSquawkWatchDialogHost
import eu.darken.apl.watch.ui.details.WatchDetailsSheetHost
import javax.inject.Inject

class WatchNavigation @Inject constructor() : NavigationEntry {
    override fun EntryProviderScope<NavKey>.setup() {
        entry<DestinationWatchList> {
            WatchListScreenHost()
        }
        entry<DestinationWatchDetails>(
            metadata = BottomSheetSceneStrategy.bottomSheet(),
        ) { dest ->
            WatchDetailsSheetHost(watchId = dest.watchId)
        }
        entry<DestinationCreateAircraftWatch>(
            metadata = BottomSheetSceneStrategy.bottomSheet(),
        ) { dest ->
            CreateAircraftWatchDialogHost(hex = dest.hex, note = dest.note)
        }
        entry<DestinationCreateFlightWatch>(
            metadata = BottomSheetSceneStrategy.bottomSheet(),
        ) { dest ->
            CreateFlightWatchDialogHost(callsign = dest.callsign, note = dest.note)
        }
        entry<DestinationCreateSquawkWatch>(
            metadata = BottomSheetSceneStrategy.bottomSheet(),
        ) { dest ->
            CreateSquawkWatchDialogHost(squawk = dest.squawk, note = dest.note)
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class WatchNavigationModule {
    @Binds
    @IntoSet
    abstract fun navigation(nav: WatchNavigation): NavigationEntry
}
