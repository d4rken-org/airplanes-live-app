package eu.darken.apl.feeder.core.api

import dagger.Reusable
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.feeder.core.ReceiverId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Converter
import retrofit2.Retrofit
import java.io.IOException
import javax.inject.Inject


@Reusable
class FeederEndpoint @Inject constructor(
    private val baseClient: OkHttpClient,
    private val jsonConverterFactory: Converter.Factory,
    private val dispatcherProvider: DispatcherProvider,
) {

    private val api: FeederApi by lazy {
        val configHttpClient = baseClient.newBuilder().apply {

        }.build()

        Retrofit.Builder()
            .client(configHttpClient)
            .baseUrl("https://api.airplanes.live/")
            .addConverterFactory(jsonConverterFactory)
            .build()
            .create(FeederApi::class.java)
    }


    /** One result per ID, so a feeder the API refuses doesn't cost the others their update. */
    suspend fun getFeedInfos(ids: Set<ReceiverId>): Map<ReceiverId, Result<FeedInfos>> =
        withContext(dispatcherProvider.IO) {
            log(TAG) { "getFeedInfos(ids=$ids)" }
            fetchEach(ids) {
                log(TAG, VERBOSE) { "Getting feeder data for $it" }
                api.getFeeder(it)
            }
        }

    suspend fun getFeedStatus(): FeedStatus = withContext(dispatcherProvider.IO) {
        log(TAG) { "getFeedStatus()" }
        api.getFeedStatus()
    }

    companion object {
        private val TAG = logTag("Feeder", "Endpoint")

        /** After an I/O failure the remaining IDs fail with it, rather than each waiting out a timeout. */
        internal suspend fun <T> fetchEach(
            ids: Collection<ReceiverId>,
            fetch: suspend (ReceiverId) -> T,
        ): Map<ReceiverId, Result<T>> {
            var networkFailure: IOException? = null
            return ids.associateWith { id ->
                networkFailure?.let { return@associateWith Result.failure(it) }
                try {
                    Result.success(fetch(id))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    log(TAG, WARN) { "Network failure for $id, skipping the rest: ${e.asLog()}" }
                    networkFailure = e
                    Result.failure(e)
                } catch (e: Exception) {
                    log(TAG, WARN) { "Fetch failed for $id: ${e.asLog()}" }
                    Result.failure(e)
                }
            }
        }
    }
}
