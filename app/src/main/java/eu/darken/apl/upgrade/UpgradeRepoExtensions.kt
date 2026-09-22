package eu.darken.apl.upgrade

import kotlinx.coroutines.flow.first

/**
 * Takes the first emission and treats an unsettled one as free.
 *
 * It must never wait for a settled emission: with nothing known yet (fresh install offline, an
 * unreachable server) none may ever arrive, and the callers sit in sequential start-up paths that
 * would then never finish. Anything that has to react to a later change collects
 * [UpgradeRepo.upgradeInfo] instead.
 */
suspend fun UpgradeRepo.isProNow(): Boolean = upgradeInfo.first().let { it.isSettled && it.isPro }
