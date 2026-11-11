package eu.darken.apl.airport.ui

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.apl.common.navigation.NavigationEntry
import eu.darken.apl.common.navigation.OverlaySceneStrategy
import javax.inject.Inject

class AirportNavigation @Inject constructor() : NavigationEntry {
    override fun EntryProviderScope<NavKey>.setup() {
        entry<DestinationAirportDetails>(
            metadata = OverlaySceneStrategy.sheet(),
        ) { dest ->
            AirportDetailsSheetHost(target = dest.target)
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AirportNavigationModule {
    @Binds
    @IntoSet
    abstract fun navigation(nav: AirportNavigation): NavigationEntry
}
