package eu.darken.apl.upgrade.subscription.play

import android.app.Activity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Play Billing for the Pro subscription, with Play's types kept behind it. Response codes are
 * `BillingClient.BillingResponseCode` values. Every call connects first if needed; no failure is
 * permanent, the next call tries again.
 */
interface PlayBilling {

    val connection: StateFlow<PlayConnection>

    /**
     * Results of purchase sheets, as Play reports them. Meant for exactly one collector; events that
     * arrive before it collects are buffered. Play may report the same purchase more than once.
     */
    val purchaseUpdates: Flow<PurchaseUpdate>

    suspend fun connect(): PlayConnection

    /** The account's subscriptions, suspended ones included. */
    suspend fun queryPurchases(): PurchaseQuery

    /** Asks Play every time, the answer depends on the account and its trial eligibility. */
    suspend fun queryOffer(): OfferQuery

    /** Opens Play's purchase sheet for the offer [queryOffer] would return right now. */
    suspend fun launch(activity: Activity): LaunchOutcome

    /** Shows Play's transactional messages, e.g. to fix a declined payment. */
    suspend fun showInAppMessages(activity: Activity, onSubscriptionStatusUpdated: () -> Unit)
}

sealed interface PlayConnection {
    /** Not connected yet, or Play dropped the connection. */
    data object Disconnected : PlayConnection

    data object Connecting : PlayConnection

    data object Ready : PlayConnection

    /** E.g. `BILLING_UNAVAILABLE` when Play is missing or not signed in. */
    data class Unavailable(val code: Int) : PlayConnection
}

sealed interface PurchaseUpdate {
    /** Can contain pending purchases. */
    data class Purchases(val purchases: List<PlayPurchase>) : PurchaseUpdate

    data object Canceled : PurchaseUpdate

    data object AlreadyOwned : PurchaseUpdate

    data class Failed(val code: Int) : PurchaseUpdate
}

sealed interface PurchaseQuery {
    data class Success(val purchases: List<PlayPurchase>) : PurchaseQuery

    data class Failed(val code: Int) : PurchaseQuery
}

sealed interface OfferQuery {
    data class Found(val offer: PlayOffer) : OfferQuery

    /** Play doesn't sell the product or its base plan to this account or device. */
    data object Missing : OfferQuery

    data class Failed(val code: Int) : OfferQuery
}

/** Play's immediate answer to opening the sheet; what the user does there arrives as [PurchaseUpdate]. */
sealed interface LaunchOutcome {
    data object Launched : LaunchOutcome

    data object Canceled : LaunchOutcome

    data object AlreadyOwned : LaunchOutcome

    data object Unavailable : LaunchOutcome

    data class Failed(val code: Int) : LaunchOutcome
}
