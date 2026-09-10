package eu.darken.apl.upgrade.ui

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.apl.common.navigation.NavigationEntry
import javax.inject.Inject

class UpgradeNavigation @Inject constructor() : NavigationEntry {
    override fun EntryProviderScope<NavKey>.setup() {
        entry<DestinationUpgrade> {
            UpgradeScreenHost()
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class UpgradeNavigationModule {
    @Binds
    @IntoSet
    abstract fun navigation(nav: UpgradeNavigation): NavigationEntry
}
