package eu.darken.apl.upgrade.subscription

import android.app.Activity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Until cancelled, shows the store's messages each time the subscription is or becomes owned. */
suspend fun SubscriptionBilling.showInAppMessagesWhileOwned(activity: Activity) =
    showWhenOwned(state) { showInAppMessages(activity) }

internal suspend fun showWhenOwned(state: Flow<SubscriptionState>, show: suspend () -> Unit) {
    state
        .map { it.ownership is SubscriptionState.Ownership.Owned }
        .distinctUntilChanged()
        .collect { owned -> if (owned) show() }
}
