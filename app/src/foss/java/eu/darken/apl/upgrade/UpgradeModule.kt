package eu.darken.apl.upgrade

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import eu.darken.apl.common.debug.autoreport.DebugSettings
import eu.darken.apl.upgrade.core.UpgradeRepoFoss
import eu.darken.apl.upgrade.core.withDebugOverride
import eu.darken.apl.upgrade.subscription.SubscriptionBilling
import eu.darken.apl.upgrade.subscription.SubscriptionBillingFoss
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UpgradeModule {

    @Provides
    @Singleton
    fun upgradeRepo(repo: UpgradeRepoFoss, debugSettings: DebugSettings): UpgradeRepo =
        repo.withDebugOverride(debugSettings)

    @Provides
    @Singleton
    fun subscriptionBilling(billing: SubscriptionBillingFoss): SubscriptionBilling = billing
}
