package eu.darken.apl.common.planespotters.coil

import androidx.appcompat.content.res.AppCompatResources
import coil3.ImageLoader
import coil3.asDrawable
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.request.SuccessResult
import coil3.size.pxOrElse
import eu.darken.apl.R
import eu.darken.apl.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.planespotters.PlanespottersMeta
import eu.darken.apl.common.planespotters.api.PlanespottersEndpoint
import retrofit2.HttpException
import javax.inject.Inject

class PlanespottersFetcher(
    private val data: AircraftThumbnailQuery,
    private val options: Options,
    private val imageLoader: ImageLoader,
    private val endpoint: PlanespottersEndpoint,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        log(TAG, VERBOSE) { "Fetching $data with $options " }

        val photos = if (data.registration != null) {
            endpoint.getPhotosByRegistration(data.registration)
        } else {
            endpoint.getPhotosByHex(data.hex)
        }
        log(TAG, VERBOSE) { "Got ${photos.size} photos for $data with $options, picking first. " }


        val photo = photos.firstOrNull() ?: return ImageFetchResult(
            image = PlanespottersImage(
                AppCompatResources.getDrawable(options.context, R.drawable.aircraft_photo_unavailable)!!,
                PlanespottersMeta(
                    hex = data.hex,
                    author = null,
                    link = "https://www.planespotters.net",
                ),
            ).asImage(),
            isSampled = false,
            dataSource = DataSource.MEMORY,
        )

        val request = ImageRequest.Builder(options.context).apply {
            val largeHeight = data.large || options.size.height.pxOrElse { 128 } > photo.thumbnail.size.height
            val largeWidth = data.large || options.size.width.pxOrElse { 128 } > photo.thumbnail.size.width
            val bestSize = if (largeHeight || largeWidth) {
                log(TAG, VERBOSE) { "Picking large thumbnail" }
                photo.thumbnailLarge
            } else {
                log(TAG, VERBOSE) { "Picking small thumbnail" }
                photo.thumbnail
            }
            data(bestSize.src)
            size(options.size)
            log(TAG, VERBOSE) { "OPTION SIZE: ${options.size}" }
        }.build()

        val result = try {
            imageLoader.execute(request)
        } catch (e: HttpException) {
            throw e
        } as SuccessResult

        return ImageFetchResult(
            image = PlanespottersImage(
                result.image.asDrawable(options.context.resources),
                PlanespottersMeta(
                    hex = data.hex,
                    author = photo.photographer,
                    link = photo.link,
                ),
            ).asImage(),
            isSampled = false,
            dataSource = result.dataSource,
        )
    }

    class Factory @Inject constructor(
        private val planespottersEndpoint: PlanespottersEndpoint,
    ) : Fetcher.Factory<AircraftThumbnailQuery> {

        override fun create(
            data: AircraftThumbnailQuery,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher = PlanespottersFetcher(
            data,
            options,
            imageLoader,
            planespottersEndpoint,
        )
    }

    companion object {
        private val TAG = logTag("Planespotters", "Fetcher")
    }
}
