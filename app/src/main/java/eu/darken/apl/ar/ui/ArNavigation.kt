package eu.darken.apl.ar.ui

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.apl.common.navigation.NavigationEntry
import javax.inject.Inject

class ArNavigation @Inject constructor() : NavigationEntry {
    override fun EntryProviderScope<NavKey>.setup() {
        entry<DestinationAr> {
            ArScreenHost()
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ArNavigationModule {
    @Binds
    @IntoSet
    abstract fun navigation(nav: ArNavigation): NavigationEntry
}
