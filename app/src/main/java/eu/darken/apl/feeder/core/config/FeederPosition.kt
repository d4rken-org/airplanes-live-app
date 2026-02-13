package eu.darken.apl.feeder.core.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FeederPosition(
    @SerialName("latitude") val latitude: Double,
    @SerialName("longitude") val longitude: Double,
) {
    companion object {
        fun fromString(input: String): FeederPosition? {
            val parts = input.split(",").map { it.trim() }
            if (parts.size != 2) return null
            return try {
                FeederPosition(
                    latitude = parts[0].toDouble(),
                    longitude = parts[1].toDouble(),
                )
            } catch (_: NumberFormatException) {
                null
            }
        }
    }
}
