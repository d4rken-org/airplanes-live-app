package eu.darken.apl.feeder.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class FeederStatus {
    @SerialName("active")
    ACTIVE,

    @SerialName("inactive")
    INACTIVE,

    /** Feed is connected but currently silent (no recent messages) — not an outage. */
    @SerialName("idle")
    IDLE,

    @SerialName("data-blocked")
    DATA_BLOCKED,

    @SerialName("unknown")
    UNKNOWN,
    ;

    companion object {
        /** Maps the raw API string defensively; unknown/missing values become [UNKNOWN]. */
        fun fromApi(raw: String?): FeederStatus = when (raw?.trim()?.lowercase()) {
            "active" -> ACTIVE
            "inactive" -> INACTIVE
            "idle" -> IDLE
            "data-blocked", "data_blocked" -> DATA_BLOCKED
            else -> UNKNOWN
        }
    }
}
