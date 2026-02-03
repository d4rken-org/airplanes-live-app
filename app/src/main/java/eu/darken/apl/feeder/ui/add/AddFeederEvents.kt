package eu.darken.apl.feeder.ui.add

import java.util.UUID

sealed class AddFeederEvents {
    object StopCamera : AddFeederEvents()
    data class ShowLocalDetectionResult(val result: LocalDetectionResult) : AddFeederEvents()
    data class ShowFeederPicker(val feeders: List<DetectedFeeder>) : AddFeederEvents()
}

enum class LocalDetectionResult {
    FOUND,
    NOT_FOUND
}

data class DetectedFeeder(
    val uuid: UUID,
    val host: String,
    val label: String?,
    val latitude: Double?,
    val longitude: Double?,
)