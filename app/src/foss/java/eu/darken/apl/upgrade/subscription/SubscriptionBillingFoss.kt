package eu.darken.apl.upgrade.subscription

import android.app.Activity
import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

class SubscriptionBillingFoss @Inject constructor() : SubscriptionBilling {

    override val state: Flow<SubscriptionState> = flowOf(UNSUPPORTED)

    override fun setup() = Unit

    override suspend fun subscribe(activity: Activity): SubscribeResult = SubscribeResult.Unavailable

    override suspend fun restore(): RestoreResult = RestoreResult.Unreachable

    override fun reconcile(reason: String) = Unit

    override val manageUri: Uri? = null

    override suspend fun showInAppMessages(activity: Activity) = Unit

    companion object {
        internal val UNSUPPORTED = SubscriptionState(
            supported = false,
            offer = SubscriptionState.Offer.Unavailable,
            ownership = SubscriptionState.Ownership.None,
            submission = SubscriptionState.Submission.Idle,
        )
    }
}
