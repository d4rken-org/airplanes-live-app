package eu.darken.apl.server

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import eu.darken.apl.server.identity.DeviceKeyStore
import eu.darken.apl.server.identity.KeystoreDeviceKeyStore
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
annotation class ServerJson

@Qualifier
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
annotation class ServerSessionDataStore

@Qualifier
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
annotation class ServerAccessDataStore

@Qualifier
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
annotation class ServerFeederLinkDataStore

@InstallIn(SingletonComponent::class)
@Module
abstract class ServerModule {

    @Binds
    abstract fun deviceKeyStore(store: KeystoreDeviceKeyStore): DeviceKeyStore

    companion object {

        /**
         * The server omits every field that equals its default, so decoding must rely on the DTO
         * defaults instead of coercing, and encoding must not send values the server treats as absent.
         */
        @ServerJson
        @Provides
        @Singleton
        fun serverJson(): Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
            coerceInputValues = false
            @OptIn(ExperimentalSerializationApi::class)
            explicitNulls = false
        }

        @ServerSessionDataStore
        @Provides
        @Singleton
        fun sessionDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("server_session") }

        @ServerAccessDataStore
        @Provides
        @Singleton
        fun accessDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("server_access") }

        @ServerFeederLinkDataStore
        @Provides
        @Singleton
        fun feederLinkDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("server_feeder_link") }
    }
}
