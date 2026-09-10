package eu.darken.apl.upgrade

import kotlinx.coroutines.flow.Flow

interface UpgradeRepo {

    val upgradeInfo: Flow<Info>

    suspend fun refresh()

    interface Info {
        val type: Type

        val isPro: Boolean

        /**
         * Whether this emission reflects a real entitlement answer. It rides the same emission as
         * [isPro], so a consumer can never pair a settled flag with ownership data from another one.
         */
        val isSettled: Boolean

        val source: Source?

        val error: Throwable?
    }

    enum class Type {
        FOSS,
        GPLAY,
        ;
    }

    enum class Source {
        FEEDER,
        SUBSCRIPTION,
        ;
    }
}
