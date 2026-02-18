package eu.darken.apl.common.coil

import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.util.Logger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import eu.darken.apl.common.BuildConfigWrap
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.Logging
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.planespotters.coil.PlanespottersFetcher
import eu.darken.apl.common.planespotters.coil.PlanespottersInterceptor
import eu.darken.apl.common.planespotters.coil.PlanespottersKeyer
import eu.darken.apl.common.planespotters.coil.PlanespottersThumbnailKeyer
import javax.inject.Provider
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class CoilModule {

    @Singleton
    @Provides
    fun imageLoader(
        @ApplicationContext context: Context,
        dispatcherProvider: DispatcherProvider,
        planespottersFetcherFactory: PlanespottersFetcher.Factory,
    ): ImageLoader = ImageLoader.Builder(context).apply {
        if (BuildConfigWrap.DEBUG) {
            val coilLogger = object : Logger {
                override var minLevel: Logger.Level = Logger.Level.Verbose
                override fun log(tag: String, level: Logger.Level, message: String?, throwable: Throwable?) {
                    val priority = when (level) {
                        Logger.Level.Verbose, Logger.Level.Debug -> Logging.Priority.VERBOSE
                        Logger.Level.Info -> Logging.Priority.INFO
                        Logger.Level.Warn -> Logging.Priority.WARN
                        Logger.Level.Error -> Logging.Priority.ERROR
                    }
                    log("Coil:$tag", priority) { "$message ${throwable?.asLog()}" }
                }
            }
            logger(coilLogger)
        }
        components {
            add(planespottersFetcherFactory)
            add(PlanespottersKeyer())
            add(PlanespottersThumbnailKeyer())
            add(PlanespottersInterceptor())
        }
        fetcherCoroutineContext(
            dispatcherProvider.Default.limitedParallelism(
                (Runtime.getRuntime().availableProcessors() - 1).coerceAtLeast(2)
            )
        )
    }.build()

    @Singleton
    @Provides
    fun imageLoaderFactory(imageLoaderSource: Provider<ImageLoader>): SingletonImageLoader.Factory =
        SingletonImageLoader.Factory {
            log(TAG) { "Preparing imageloader factory" }
            imageLoaderSource.get()
        }

    companion object {
        private val TAG = logTag("Coil", "Module")
    }
}
