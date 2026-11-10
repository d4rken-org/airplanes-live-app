package eu.darken.apl.watch.ui.thumbnail

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.disk.DiskCache
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

class LocationThumbnailFetcher(
    private val data: LocationThumbnailQuery,
    private val options: Options,
    private val imageLoader: ImageLoader,
    private val renderer: LocationThumbnailRenderer,
    private val queue: LocationThumbnailRenderQueue,
    private val dispatcherProvider: DispatcherProvider,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val key = data.cacheKey
        if (!LocationThumbnailGeometry.isRenderable(data)) {
            log(TAG, WARN) { "not renderable $key" }
            throw LocationThumbnailNotRenderableException(key)
        }

        val readCache = imageLoader.diskCache?.takeIf { options.diskCachePolicy.readEnabled }
        val writeCache = imageLoader.diskCache?.takeIf { options.diskCachePolicy.writeEnabled }

        readFromDisk(readCache, key)?.let { return it }

        return try {
            queue.runExclusive {
                // Requests that missed together wait here; only the first one renders
                readFromDisk(readCache, key) ?: renderAndStore(writeCache, key)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(TAG, WARN) { "failed $key: ${e.asLog()}" }
            throw e
        }
    }

    private suspend fun readFromDisk(diskCache: DiskCache?, key: String): ImageFetchResult? {
        diskCache ?: return null
        val bitmap = withContext(dispatcherProvider.IO) {
            val snapshot = diskCache.openSnapshot(key) ?: return@withContext null
            val decoded = snapshot.use { BitmapFactory.decodeFile(it.data.toFile().path) }
            if (decoded == null) {
                log(TAG, WARN) { "undecodable disk entry $key" }
                diskCache.remove(key)
            }
            decoded
        } ?: return null

        log(TAG) { "disk hit $key" }
        return ImageFetchResult(
            image = bitmap.asImage(),
            isSampled = false,
            dataSource = DataSource.DISK,
        )
    }

    private suspend fun renderAndStore(diskCache: DiskCache?, key: String): ImageFetchResult {
        val bitmap = renderer.render(data)
        log(TAG) { "rendered $key" }

        if (diskCache != null) withContext(dispatcherProvider.IO) { writeToDisk(diskCache, key, bitmap) }

        return ImageFetchResult(
            image = bitmap.asImage(),
            isSampled = false,
            dataSource = DataSource.NETWORK,
        )
    }

    private fun writeToDisk(diskCache: DiskCache, key: String, bitmap: Bitmap) {
        val editor = diskCache.openEditor(key)
        if (editor == null) {
            log(TAG, WARN) { "disk cache busy, not storing $key" }
            return
        }
        try {
            diskCache.fileSystem.write(editor.data) {
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream())) {
                    throw IOException("PNG encoding failed")
                }
            }
            editor.commit()
        } catch (e: Exception) {
            log(TAG, WARN) { "storing $key failed: ${e.asLog()}" }
            try {
                editor.abort()
            } catch (abortError: Exception) {
                log(TAG, WARN) { "aborting $key failed: ${abortError.asLog()}" }
            }
        }
    }

    class Factory @Inject constructor(
        private val renderer: LocationThumbnailRenderer,
        private val queue: LocationThumbnailRenderQueue,
        private val dispatcherProvider: DispatcherProvider,
    ) : Fetcher.Factory<LocationThumbnailQuery> {

        override fun create(
            data: LocationThumbnailQuery,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher = LocationThumbnailFetcher(
            data = data,
            options = options,
            imageLoader = imageLoader,
            renderer = renderer,
            queue = queue,
            dispatcherProvider = dispatcherProvider,
        )
    }

    companion object {
        private val TAG = logTag("Watch", "Thumbnail")
    }
}

class LocationThumbnailNotRenderableException(key: String) : Exception("Area beyond the map's latitude limit: $key")
