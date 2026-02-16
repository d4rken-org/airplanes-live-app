package eu.darken.apl.search.ui

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.apl.common.navigation.NavigationEntry
import eu.darken.apl.search.ui.actions.DestinationSearchAction
import eu.darken.apl.search.ui.actions.SearchActionSheetHost
import javax.inject.Inject

class SearchNavigation @Inject constructor() : NavigationEntry {
    override fun EntryProviderScope<NavKey>.setup() {
        entry<DestinationSearch> { dest ->
            SearchScreenHost(
                targetHexes = dest.targetHexes,
                targetSquawks = dest.targetSquawks,
                targetCallsigns = dest.targetCallsigns,
            )
        }
        entry<DestinationSearchAction> { dest ->
            SearchActionSheetHost(hex = dest.hex)
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SearchNavigationModule {
    @Binds
    @IntoSet
    abstract fun navigation(nav: SearchNavigation): NavigationEntry
}
