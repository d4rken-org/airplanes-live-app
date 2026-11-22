package eu.darken.apl.upgrade.subscription.submit

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import eu.darken.apl.common.datastore.createValue
import eu.darken.apl.common.datastore.value
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubscriptionTokenStore @Inject constructor(
    @SubscriptionTokenDataStore dataStore: DataStore<Preferences>,
    json: Json,
) {

    private val value = dataStore.createValue<SubscriptionTokens>(
        key = KEY,
        defaultValue = SubscriptionTokens(),
        // A state written by a newer build becomes the property's default instead of losing every record
        json = Json(json) { coerceInputValues = true },
        onErrorFallbackToDefault = true,
    )

    val tokens: Flow<SubscriptionTokens> = value.flow

    suspend fun current(): SubscriptionTokens = value.value()

    suspend fun update(block: (SubscriptionTokens) -> SubscriptionTokens): SubscriptionTokens = value.update(block).new

    companion object {
        internal const val KEY = "subscription.tokens"
    }
}
