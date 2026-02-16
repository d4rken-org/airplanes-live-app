package eu.darken.apl.main.ui.main

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.apl.common.navigation.NavigationEntry
import eu.darken.apl.main.ui.DestinationMain
import javax.inject.Inject

class MainNavigation @Inject constructor() : NavigationEntry {
    override fun EntryProviderScope<NavKey>.setup() {
        entry<DestinationMain> {
            MainScreenHost()
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class MainNavigationModule {
    @Binds
    @IntoSet
    abstract fun navigation(nav: MainNavigation): NavigationEntry
}
