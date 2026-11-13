package eu.darken.apl.watch.ui.thumbnail

import android.graphics.Bitmap

interface LocationThumbnailRenderer {
    suspend fun render(query: LocationThumbnailQuery): Bitmap

    suspend fun render(query: MapPointThumbnailQuery): Bitmap
}

class LocationThumbnailRenderException(message: String) : Exception(message)
