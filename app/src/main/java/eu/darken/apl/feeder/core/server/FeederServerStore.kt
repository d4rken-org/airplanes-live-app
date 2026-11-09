package eu.darken.apl.feeder.core.server

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.apl.common.datastore.createValue
import eu.darken.apl.common.datastore.value
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.feederServerDataStore by preferencesDataStore(name = "settings_feeder_server")

/** Excluded from backups, the state belongs to one installation. */
@Singleton
class FeederServerStore internal constructor(
    dataStore: DataStore<Preferences>,
    json: Json,
) {

    @Inject constructor(
        @ApplicationContext context: Context,
        json: Json,
    ) : this(context.feederServerDataStore, json)

    private val value = dataStore.createValue(
        KEY_STATE,
        FeederServerState(),
        json,
        onErrorFallbackToDefault = true,
    )

    val state: Flow<FeederServerState> = value.flow.distinctUntilChanged()

    suspend fun current(): FeederServerState = value.value()

    /** Atomic against other updates, [change] gets the latest stored state. */
    suspend fun update(change: (FeederServerState) -> FeederServerState): FeederServerState =
        value.update(change).new

    suspend fun wipe() {
        value.update { null }
    }

    companion object {
        internal const val KEY_STATE = "feeder.server.state"
    }
}
