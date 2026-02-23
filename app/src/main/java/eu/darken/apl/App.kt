package eu.darken.apl

import android.app.Application
import android.util.Log.VERBOSE
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.SingletonImageLoader
import dagger.hilt.android.HiltAndroidApp
import eu.darken.apl.common.BuildConfigWrap
import eu.darken.apl.common.debug.autoreport.AutoReporting
import eu.darken.apl.common.debug.logging.LogCatLogger
import eu.darken.apl.common.debug.logging.Logging
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.feeder.core.monitor.FeederWorkerHelper
import eu.darken.apl.watch.core.alerts.WatchWorkerHelper
import javax.inject.Inject

@HiltAndroidApp
open class App : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var bugReporter: AutoReporting
    @Inject lateinit var feederWorkerHelper: FeederWorkerHelper
    @Inject lateinit var watchWorkerHelper: WatchWorkerHelper
    @Inject lateinit var imageLoaderFactory: SingletonImageLoader.Factory

    override fun onCreate() {
        super.onCreate()
        if (BuildConfigWrap.DEBUG) {
            Logging.install(LogCatLogger())
            log(TAG) { "BuildConfig.DEBUG=true" }
        }

        bugReporter.setup()

        SingletonImageLoader.setSafe(imageLoaderFactory)

        feederWorkerHelper.setup()
        watchWorkerHelper.setup()

        log(TAG) { "onCreate() done! ${Exception().asLog()}" }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(VERBOSE)
            .setWorkerFactory(workerFactory)
            .build()

    companion object {
        internal val TAG = logTag("App")
    }
}
