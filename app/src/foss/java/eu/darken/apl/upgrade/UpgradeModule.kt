package eu.darken.apl.upgrade

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import eu.darken.apl.upgrade.core.UpgradeRepoFoss
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class UpgradeModule {

    @Binds
    @Singleton
    abstract fun upgradeRepo(repo: UpgradeRepoFoss): UpgradeRepo
}
