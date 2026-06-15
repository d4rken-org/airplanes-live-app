package eu.darken.apl.feeder.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Locally cached feeder list, tagged with the account [ownerId] it belongs to so a different
 * account never briefly sees the previous account's feeders.
 */
@Serializable
data class FeederCache(
    @SerialName("ownerId") val ownerId: Long? = null,
    @SerialName("feeders") val feeders: List<Feeder> = emptyList(),
)
