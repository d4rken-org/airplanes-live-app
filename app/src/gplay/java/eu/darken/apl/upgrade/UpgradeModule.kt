package eu.darken.apl.upgrade

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import eu.darken.apl.common.debug.autoreport.DebugSettings
import eu.darken.apl.upgrade.core.UpgradeRepoGplay
import eu.darken.apl.upgrade.core.withDebugOverride
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UpgradeModule {

    @Provides
    @Singleton
    fun upgradeRepo(repo: UpgradeRepoGplay, debugSettings: DebugSettings): UpgradeRepo =
        repo.withDebugOverride(debugSettings)
}
