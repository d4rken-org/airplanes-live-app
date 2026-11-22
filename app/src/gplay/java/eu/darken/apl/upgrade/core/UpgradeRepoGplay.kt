package eu.darken.apl.upgrade.core

import eu.darken.apl.upgrade.UpgradeRepo
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpgradeRepoGplay @Inject constructor(
    private val feederSource: FeederUpgradeSource,
) : UpgradeRepo {

    override val upgradeInfo: Flow<UpgradeRepo.Info> = feederSource.state(UpgradeRepo.Type.GPLAY)

    override suspend fun refresh() = feederSource.refresh()
}
