package eu.darken.apl.server.deletion

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import eu.darken.apl.server.ServerDeletionDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeletionStateStore @Inject constructor(
    @param:ServerDeletionDataStore private val dataStore: DataStore<Preferences>,
) {

    val state: Flow<DeletionState> = dataStore.data
        .map { prefs ->
            val stored = prefs[STATE]
            DeletionState.entries.firstOrNull { it.name == stored } ?: DeletionState.NONE
        }
        .distinctUntilChanged()

    suspend fun current(): DeletionState = state.first()

    suspend fun set(state: DeletionState) {
        dataStore.edit {
            if (state == DeletionState.NONE) it.remove(STATE) else it[STATE] = state.name
        }
    }

    companion object {
        private val STATE = stringPreferencesKey("deletion.state")
    }
}
