package eu.darken.apl.upgrade.subscription

import android.app.Activity
import android.net.Uri
import kotlinx.coroutines.flow.Flow

interface SubscriptionBilling {

    val state: Flow<SubscriptionState>

    /** Called once from App.onCreate. */
    fun setup()

    suspend fun subscribe(activity: Activity): SubscribeResult

    suspend fun restore()

    fun reconcile(reason: String)

    /** The store's subscription management deep link, null where unsupported. */
    val manageUri: Uri?

    /** Called by MainActivity while resumed when [state] says the subscription is owned. */
    suspend fun showInAppMessages(activity: Activity)
}
