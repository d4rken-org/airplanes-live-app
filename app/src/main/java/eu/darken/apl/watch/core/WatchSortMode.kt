package eu.darken.apl.watch.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class WatchSortMode {
    @SerialName("by_note")
    BY_NOTE,

    @SerialName("by_last_seen")
    BY_LAST_SEEN,

    @SerialName("by_created")
    BY_CREATED,
}
