package eu.darken.apl.feeder.core.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FeederPosition(
    @SerialName("latitude") val latitude: Double,
    @SerialName("longitude") val longitude: Double,
)
