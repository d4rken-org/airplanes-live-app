package eu.darken.apl.feeder.core.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class FeederSortMode {
    @SerialName("by_label")
    BY_LABEL,

    @SerialName("by_status")
    BY_STATUS,

    @SerialName("by_last_seen")
    BY_LAST_SEEN,
}
