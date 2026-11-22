package eu.darken.apl.upgrade

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import eu.darken.apl.common.debug.autoreport.DebugSettings
import eu.darken.apl.upgrade.core.UpgradeRepoGplay
import eu.darken.apl.upgrade.core.withDebugOverride
import eu.darken.apl.upgrade.subscription.SubscriptionBilling
import eu.darken.apl.upgrade.subscription.SubscriptionBillingGplay
import eu.darken.apl.upgrade.subscription.play.PlayBilling
import eu.darken.apl.upgrade.subscription.play.PlayBillingClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UpgradeModule {

    @Provides
    @Singleton
    fun upgradeRepo(repo: UpgradeRepoGplay, debugSettings: DebugSettings): UpgradeRepo =
        repo.withDebugOverride(debugSettings)

    @Provides
    @Singleton
    fun playBilling(client: PlayBillingClient): PlayBilling = client

    @Provides
    @Singleton
    fun subscriptionBilling(billing: SubscriptionBillingGplay): SubscriptionBilling = billing
}
