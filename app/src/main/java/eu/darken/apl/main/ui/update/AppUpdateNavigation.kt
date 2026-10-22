package eu.darken.apl.main.ui.update

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.apl.common.navigation.BootstrapDestination
import eu.darken.apl.common.navigation.NavigationEntry
import kotlinx.serialization.Serializable
import javax.inject.Inject

@Serializable
data object DestinationAppUpdate : BootstrapDestination

class AppUpdateNavigation @Inject constructor() : NavigationEntry {
    override fun EntryProviderScope<NavKey>.setup() {
        entry<DestinationAppUpdate> {
            AppUpdateScreenHost()
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AppUpdateNavigationModule {
    @Binds
    @IntoSet
    abstract fun navigation(nav: AppUpdateNavigation): NavigationEntry
}
