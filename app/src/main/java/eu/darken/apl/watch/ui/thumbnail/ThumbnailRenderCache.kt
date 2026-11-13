package eu.darken.apl.watch.ui.thumbnail

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.disk.DiskCache
import coil3.fetch.FetchResult
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

/** Serves a rendered map thumbnail from Coil's disk cache, else renders it in the [LocationThumbnailRenderQueue]. */
class ThumbnailRenderCache @Inject constructor(
    private val queue: LocationThumbnailRenderQueue,
    private val dispatcherProvider: DispatcherProvider,
) {

    suspend fun fetch(
        key: String,
        options: Options,
        imageLoader: ImageLoader,
        render: suspend () -> Bitmap,
    ): FetchResult {
        val readCache = imageLoader.diskCache?.takeIf { options.diskCachePolicy.readEnabled }
        val writeCache = imageLoader.diskCache?.takeIf { options.diskCachePolicy.writeEnabled }

        readFromDisk(readCache, key)?.let { return it }

        return try {
            queue.runExclusive {
                // Requests that missed together wait here; only the first one renders
                readFromDisk(readCache, key) ?: renderAndStore(writeCache, key, render)
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

    private suspend fun renderAndStore(
        diskCache: DiskCache?,
        key: String,
        render: suspend () -> Bitmap,
    ): ImageFetchResult {
        val bitmap = render()
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

    companion object {
        private val TAG = logTag("Watch", "Thumbnail")
    }
}
