package eu.darken.apl.search.core

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.apl.common.datastore.createValue
import eu.darken.apl.common.debug.logging.logTag
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import eu.darken.apl.common.datastore.createValue as createJsonValue

@Singleton
class SearchSettings @Inject constructor(
    @param:ApplicationContext private val context: Context,
    json: Json,
) {

    private val Context.dataStore by preferencesDataStore(name = "settings_search")

    val searchLocationDismissed = context.dataStore.createValue("search.location.dismissed", false)
    val lastInput = context.dataStore.createJsonValue(
        "search.query",
        SearchInput(),
        json,
        onErrorFallbackToDefault = true,
    )

    companion object {
        internal val TAG = logTag("Search", "Settings")
    }
}
