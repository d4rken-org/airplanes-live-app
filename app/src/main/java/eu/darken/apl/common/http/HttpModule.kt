package eu.darken.apl.common.http

import android.content.Context
import android.os.Build
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import eu.darken.apl.common.BuildConfigWrap
import eu.darken.apl.common.datastore.valueBlocking
import eu.darken.apl.common.debug.autoreport.DebugSettings
import eu.darken.apl.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Converter
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class HttpModule {

    @Reusable
    @Provides
    fun loggingInterceptor(
        debugSettings: DebugSettings? = null,
    ): HttpLoggingInterceptor {
        val logger = HttpLoggingInterceptor.Logger {
            log(TAG, VERBOSE) { it }
        }
        return HttpLoggingInterceptor(logger).apply {
            level = if (debugSettings?.isDebugMode?.valueBlocking != false) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.BASIC
            }
        }
    }

    @Qualifier
    @MustBeDocumented
    @Retention(AnnotationRetention.RUNTIME)
    annotation class UserAgent

    @UserAgent
    @Reusable
    @Provides
    fun userAgent(): String =
        "${BuildConfigWrap.APPLICATION_ID}/${BuildConfigWrap.VERSION_NAME} " +
            "(Android ${Build.VERSION.RELEASE}; ${Build.MODEL}; +https://github.com/d4rken-org/airplanes-live-app)"

    @Singleton
    @Provides
    fun baseHttpClient(
        @BaseCache cache: Cache? = null,
        loggingInterceptor: HttpLoggingInterceptor = loggingInterceptor(),
        @UserAgent userAgent: String = userAgent(),
    ): OkHttpClient = OkHttpClient().newBuilder().apply {
        if (cache != null) {
            cache(cache)
        }
        connectTimeout(20L, TimeUnit.SECONDS)
        readTimeout(20L, TimeUnit.SECONDS)
        writeTimeout(20L, TimeUnit.SECONDS)
        retryOnConnectionFailure(true)
        addInterceptor(loggingInterceptor)

        addInterceptor { chain ->
            val request = chain.request().newBuilder().apply {
                header("User-Agent", userAgent)
            }.build()
            chain.proceed(request)
        }
    }.build()

    @BaseCache
    @Provides
    @Singleton
    fun baseHttpCache(@ApplicationContext context: Context): Cache {
        val cacheDir = File(context.cacheDir, "http_base_cache")
        return Cache(cacheDir, 1024L * 1024L * 20) // 20 MB
    }

    @Reusable
    @Provides
    fun jsonConverter(json: Json): Converter.Factory {
        val contentType = "application/json".toMediaType()
        return json.asConverterFactory(contentType)
    }

    @Qualifier
    @MustBeDocumented
    @Retention(AnnotationRetention.RUNTIME)
    annotation class BaseCache

    companion object {
        private val TAG = logTag("Http")
    }
}
