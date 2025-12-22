package eu.darken.apl.map.core

import kotlinx.serialization.Serializable

@Serializable
data class SavedCamera(
    val lat: Double,
    val lon: Double,
    val zoom: Double,
) {
    fun toCamera(): MapOptions.Camera = MapOptions.Camera(lat, lon, zoom)

    companion object {
        fun from(camera: MapOptions.Camera): SavedCamera = SavedCamera(camera.lat, camera.lon, camera.zoom)
    }
}
