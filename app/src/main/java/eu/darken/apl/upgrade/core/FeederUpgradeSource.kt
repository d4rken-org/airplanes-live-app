package eu.darken.apl.upgrade.core

import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.access.AccessState
import eu.darken.apl.upgrade.UpgradeRepo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The tier the server derived from a verified feeder entitlement. Shared by both flavors, an
 * installation is upgraded by feeding regardless of where the app came from.
 */
@Singleton
class FeederUpgradeSource @Inject constructor(
    private val accessRepo: AccessRepo,
) {

    data class Info(
        override val type: UpgradeRepo.Type,
        override val isPro: Boolean,
        override val isSettled: Boolean,
        override val source: UpgradeRepo.Source?,
        override val error: Throwable? = null,
    ) : UpgradeRepo.Info

    /** [UpgradeRepo.Info.type] names the flavor, which only the flavor's repo knows. */
    fun state(type: UpgradeRepo.Type): Flow<UpgradeRepo.Info> = accessRepo.state.map { access ->
        when (access?.tier) {
            // Nothing fetched or restored yet, this says nothing about the entitlement
            null -> Info(type = type, isPro = false, isSettled = false, source = null)
            AccessState.Tier.FEEDER -> Info(
                type = type,
                isPro = true,
                isSettled = true,
                source = UpgradeRepo.Source.FEEDER,
            )

            AccessState.Tier.FREE -> Info(type = type, isPro = false, isSettled = true, source = null)
        }
    }

    suspend fun refresh() = accessRepo.refresh("upgrade")
}
