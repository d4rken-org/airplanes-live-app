package eu.darken.apl.main.core

import eu.darken.apl.upgrade.UpgradeRepo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackgroundSync @Inject constructor(
    generalSettings: GeneralSettings,
    upgradeRepo: UpgradeRepo,
) {

    /**
     * The level in effect. An unsettled tier counts as free, so `level.first()` never waits for a
     * settled emission: with nothing known yet (fresh install offline, an unreachable server) none may
     * ever arrive, and one-shot readers sit in paths that would then never finish.
     */
    val level: Flow<BackgroundSyncLevel> = combine(
        generalSettings.backgroundSyncLevel.flow,
        upgradeRepo.upgradeInfo.map { it.isSettled && it.isPro },
    ) { chosen, isPro ->
        BackgroundSyncLevel.effective(chosen, isPro)
    }.distinctUntilChanged()
}
