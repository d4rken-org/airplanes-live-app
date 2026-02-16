package eu.darken.apl.main.ui.onboarding

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.apl.common.navigation.NavigationEntry
import eu.darken.apl.main.ui.DestinationPrivacy
import eu.darken.apl.main.ui.DestinationWelcome
import eu.darken.apl.main.ui.onboarding.privacy.PrivacyScreenHost
import eu.darken.apl.main.ui.onboarding.welcome.WelcomeScreenHost
import javax.inject.Inject

class OnboardingNavigation @Inject constructor() : NavigationEntry {
    override fun EntryProviderScope<NavKey>.setup() {
        entry<DestinationWelcome> {
            WelcomeScreenHost()
        }
        entry<DestinationPrivacy> {
            PrivacyScreenHost()
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class OnboardingNavigationModule {
    @Binds
    @IntoSet
    abstract fun navigation(nav: OnboardingNavigation): NavigationEntry
}
