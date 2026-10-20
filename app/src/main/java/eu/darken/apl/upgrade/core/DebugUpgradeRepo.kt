package eu.darken.apl.upgrade.core

import eu.darken.apl.common.BuildConfigWrap
import eu.darken.apl.common.debug.autoreport.DebugSettings
import eu.darken.apl.upgrade.UpgradeRepo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class DebugUpgradeRepo(
    private val delegate: UpgradeRepo,
    private val debugSettings: DebugSettings,
) : UpgradeRepo {

    override val upgradeInfo: Flow<UpgradeRepo.Info> = combine(
        delegate.upgradeInfo,
        debugSettings.isFakeUpgradeEnabled.flow,
    ) { info, isFakeUpgrade ->
        if (isFakeUpgrade) FakeInfo(type = info.type) else info
    }

    override suspend fun refresh() = delegate.refresh()

    private data class FakeInfo(
        override val type: UpgradeRepo.Type,
    ) : UpgradeRepo.Info {
        override val isPro: Boolean = true
        override val isSettled: Boolean = true
        override val source: UpgradeRepo.Source? = null
        override val error: Throwable? = null
    }
}

fun UpgradeRepo.withDebugOverride(
    debugSettings: DebugSettings,
    buildType: BuildConfigWrap.BuildType = BuildConfigWrap.BUILD_TYPE,
): UpgradeRepo = if (buildType == BuildConfigWrap.BuildType.DEV) DebugUpgradeRepo(this, debugSettings) else this
