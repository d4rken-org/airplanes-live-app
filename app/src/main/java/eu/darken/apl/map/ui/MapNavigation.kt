package eu.darken.apl.map.ui

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.apl.common.navigation.NavigationEntry
import eu.darken.apl.map.core.MapHandler
import javax.inject.Inject

class MapNavigation @Inject constructor(
    private val mapHandlerFactory: MapHandler.Factory,
) : NavigationEntry {
    override fun EntryProviderScope<NavKey>.setup() {
        entry<DestinationMap> { dest ->
            MapScreenHost(
                mapOptions = dest.mapOptions,
                mapHandlerFactory = mapHandlerFactory,
            )
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class MapNavigationModule {
    @Binds
    @IntoSet
    abstract fun navigation(nav: MapNavigation): NavigationEntry
}
