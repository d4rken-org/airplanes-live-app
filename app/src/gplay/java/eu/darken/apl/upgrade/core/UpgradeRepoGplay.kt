package eu.darken.apl.upgrade.core

import eu.darken.apl.upgrade.UpgradeRepo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpgradeRepoGplay @Inject constructor(
    private val feederSource: FeederUpgradeSource,
) : UpgradeRepo {

    private data class Info(
        override val isPro: Boolean,
        override val isSettled: Boolean,
        override val source: UpgradeRepo.Source?,
        override val error: Throwable?,
    ) : UpgradeRepo.Info {
        override val type = UpgradeRepo.Type.GPLAY
    }

    // A subscription source joins this list, the flavor seam is the composition and not the screen
    private val sources: List<Flow<UpgradeRepo.Info>> = listOf(
        feederSource.state(UpgradeRepo.Type.GPLAY),
    )

    override val upgradeInfo: Flow<UpgradeRepo.Info> = combine(sources) { infos -> infos.merge() }

    /** Upgraded by any source that says so, settled only once every source has answered. */
    private fun Array<UpgradeRepo.Info>.merge(): UpgradeRepo.Info {
        val upgraded = firstOrNull { it.isSettled && it.isPro }
        return Info(
            isPro = upgraded != null,
            isSettled = all { it.isSettled },
            source = upgraded?.source,
            error = firstNotNullOfOrNull { it.error },
        )
    }

    override suspend fun refresh() = feederSource.refresh()
}
