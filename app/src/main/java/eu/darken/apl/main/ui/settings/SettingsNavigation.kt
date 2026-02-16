package eu.darken.apl.main.ui.settings

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.apl.common.navigation.NavigationEntry
import eu.darken.apl.feeder.ui.settings.FeederSettingsScreenHost
import eu.darken.apl.main.ui.settings.acks.AcknowledgementsScreenHost
import eu.darken.apl.main.ui.settings.general.GeneralSettingsScreenHost
import eu.darken.apl.main.ui.settings.support.SupportScreenHost
import eu.darken.apl.map.ui.settings.MapSettingsScreenHost
import eu.darken.apl.watch.ui.settings.WatchSettingsScreenHost
import javax.inject.Inject

class SettingsNavigation @Inject constructor() : NavigationEntry {
    override fun EntryProviderScope<NavKey>.setup() {
        entry<DestinationSettingsIndex> {
            SettingsIndexScreenHost()
        }
        entry<DestinationGeneralSettings> {
            GeneralSettingsScreenHost()
        }
        entry<DestinationMapSettings> {
            MapSettingsScreenHost()
        }
        entry<DestinationFeederSettings> {
            FeederSettingsScreenHost()
        }
        entry<DestinationWatchSettings> {
            WatchSettingsScreenHost()
        }
        entry<DestinationAcknowledgements> {
            AcknowledgementsScreenHost()
        }
        entry<DestinationSupport> {
            SupportScreenHost()
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsNavigationModule {
    @Binds
    @IntoSet
    abstract fun navigation(nav: SettingsNavigation): NavigationEntry
}
