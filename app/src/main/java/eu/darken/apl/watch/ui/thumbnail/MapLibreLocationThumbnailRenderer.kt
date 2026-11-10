package eu.darken.apl.watch.ui.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.map.core.NativeMapStyle
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.snapshotter.MapSnapshot
import org.maplibre.android.snapshotter.MapSnapshotter
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.hypot
import kotlin.math.roundToInt

class MapLibreLocationThumbnailRenderer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatcherProvider: DispatcherProvider,
) : LocationThumbnailRenderer {

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    override suspend fun render(query: LocationThumbnailQuery): Bitmap {
        val snapshot = withContext(dispatcherProvider.Main) { takeSnapshot(query) }
        return withContext(dispatcherProvider.Default) { drawArea(snapshot, query) }
    }

    private class Snapshot(
        val bitmap: Bitmap,
        val center: PointF,
        val north: PointF,
    )

    private suspend fun takeSnapshot(query: LocationThumbnailQuery): Snapshot = suspendCancellableCoroutine { cont ->
        MapLibre.getInstance(context)

        val camera = LocationThumbnailGeometry.camera(query)
        val center = LatLng(camera.latitude, camera.longitude)
        val north = LatLng(
            query.latitude + LocationThumbnailGeometry.latitudeSpan(query.radiusInMeters),
            query.longitude,
        )
        val options = MapSnapshotter.Options(query.widthDp, query.heightDp)
            .withPixelRatio(query.density)
            .withCameraPosition(
                CameraPosition.Builder()
                    .target(center)
                    .zoom(camera.zoom)
                    .build()
            )
            .withStyle(NativeMapStyle.AUTO.styleUrl(query.darkTheme))
            .withLogo(false)
            .withAttribution(false)
        val snapshotter = MapSnapshotter(context, options)

        // cancel() throws off the main thread, and this handler may run on any thread
        cont.invokeOnCancellation { mainHandler.post { snapshotter.cancel() } }

        snapshotter.start(
            object : MapSnapshotter.SnapshotReadyCallback {
                override fun onSnapshotReady(snapshot: MapSnapshot) {
                    if (!cont.isActive) return
                    cont.resume(
                        Snapshot(
                            bitmap = snapshot.bitmap,
                            center = snapshot.pixelForLatLng(center),
                            north = snapshot.pixelForLatLng(north),
                        )
                    )
                }
            },
            object : MapSnapshotter.ErrorHandler {
                override fun onError(error: String) {
                    if (!cont.isActive) return
                    cont.resumeWithException(LocationThumbnailRenderException(error))
                }
            },
        )
    }

    private fun drawArea(snapshot: Snapshot, query: LocationThumbnailQuery): Bitmap {
        val source = snapshot.bitmap
        val bitmap = if (source.isMutable) source else source.copy(Bitmap.Config.ARGB_8888, true)

        val scale = pixelScale(snapshot.center, query, bitmap)
        val centerX = snapshot.center.x * scale
        val centerY = snapshot.center.y * scale
        val radius = hypot(snapshot.north.x - snapshot.center.x, snapshot.north.y - snapshot.center.y) * scale

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = withAlpha(query.circleColor, FILL_ALPHA)
        }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = STROKE_WIDTH_DP * query.density
            color = query.circleColor
        }
        Canvas(bitmap).apply {
            drawCircle(centerX, centerY, radius, fill)
            drawCircle(centerX, centerY, radius, stroke)
        }
        return bitmap
    }

    // pixelForLatLng may answer in logical or in bitmap pixels; the camera center tells which
    private fun pixelScale(center: PointF, query: LocationThumbnailQuery, bitmap: Bitmap): Float {
        val logicalDistance = hypot(center.x - query.widthDp / 2f, center.y - query.heightDp / 2f)
        val bitmapDistance = hypot(center.x - bitmap.width / 2f, center.y - bitmap.height / 2f)
        return if (logicalDistance < bitmapDistance) bitmap.width.toFloat() / query.widthDp else 1f
    }

    private fun withAlpha(color: Int, fraction: Float): Int {
        val alpha = (Color.alpha(color) * fraction).roundToInt()
        return (color and 0x00FFFFFF) or (alpha shl 24)
    }

    companion object {
        private const val FILL_ALPHA = 0.18f
        private const val STROKE_WIDTH_DP = 2f
    }
}
