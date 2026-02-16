package eu.darken.apl.search.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.apl.common.datastore.createValue
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.search.ui.SearchViewModel
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
// Import the specific createValue function from DataStoreValueJson
import eu.darken.apl.common.datastore.createValue as createJsonValue

@Singleton
class SearchSettings @Inject constructor(
    @param:ApplicationContext private val context: Context,
    json: Json,
) {

    private val Context.dataStore by preferencesDataStore(name = "settings_search")

    val searchLocationDismissed = context.dataStore.createValue("search.location.dismissed", false)
    val inputLastRegistration = context.dataStore.createValue("search.lastinput.registration", "HO-HOHO")
    val inputLastHex = context.dataStore.createValue("search.lastinput.hex", "3C65A3")
    val inputLastCallsign = context.dataStore.createValue("search.lastinput.callsign", "DLH453")
    val inputLastAirframe = context.dataStore.createValue("search.lastinput.airframe", "A320")
    val inputLastSquawk = context.dataStore.createValue("search.lastinput.squawk", "7700,7600,7500")
    val inputLastInteresting = context.dataStore.createValue("search.lastinput.interesting", "military,ladd,pia")
    val inputLastPosition = context.dataStore.createValue("search.lastinput.position", "Frankfurt am Main, Germany")
    val inputLastAll = context.dataStore.createValue("search.lastinput.all", "")
    val inputLastMode = context.dataStore.createJsonValue("search.lastmode", SearchViewModel.State.Mode.POSITION, json)

    companion object {
        internal val TAG = logTag("Search", "Settings")
    }
}
