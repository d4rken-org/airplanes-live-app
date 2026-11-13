package eu.darken.apl.watch.ui.thumbnail

import coil3.ImageLoader
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.request.Options
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import javax.inject.Inject

class MapPointThumbnailFetcher(
    private val data: MapPointThumbnailQuery,
    private val options: Options,
    private val imageLoader: ImageLoader,
    private val renderer: LocationThumbnailRenderer,
    private val cache: ThumbnailRenderCache,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val key = data.cacheKey
        if (!LocationThumbnailGeometry.isRenderable(data)) {
            log(TAG, WARN) { "not renderable $key" }
            throw MapPointThumbnailNotRenderableException(key)
        }

        return cache.fetch(key = key, options = options, imageLoader = imageLoader) { renderer.render(data) }
    }

    class Factory @Inject constructor(
        private val renderer: LocationThumbnailRenderer,
        private val cache: ThumbnailRenderCache,
    ) : Fetcher.Factory<MapPointThumbnailQuery> {

        override fun create(
            data: MapPointThumbnailQuery,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher = MapPointThumbnailFetcher(
            data = data,
            options = options,
            imageLoader = imageLoader,
            renderer = renderer,
            cache = cache,
        )
    }

    companion object {
        private val TAG = logTag("Watch", "Thumbnail", "Point")
    }
}

class MapPointThumbnailNotRenderableException(key: String) :
    Exception("Point beyond the map's latitude limit or empty size: $key")
