package eu.darken.apl.upgrade.subscription

import java.time.Period

/**
 * [offer], [ownership] and [submission] change independently: an offer that can't be loaded says
 * nothing about ownership, and a failed ownership check leaves [Ownership.Unknown] (or the last known
 * value), never [Ownership.None].
 */
data class SubscriptionState(
    val supported: Boolean,
    val offer: Offer,
    val ownership: Ownership,
    val submission: Submission,
) {

    sealed interface Offer {
        data object Loading : Offer

        data object Unavailable : Offer

        /** [formattedPrice] and [billingPeriod] are what is charged after the trial, if there is one. */
        data class Available(
            val formattedPrice: String,
            val billingPeriod: Period,
            val trialPeriod: Period?,
        ) : Offer
    }

    sealed interface Ownership {
        data object Unknown : Ownership

        data object None : Ownership

        /** [suspended]: the store holds the subscription after a failed payment. */
        data class Owned(val suspended: Boolean) : Ownership
    }

    /** Where handing the purchase to the server stands. */
    sealed interface Submission {
        data object Idle : Submission

        /** The store is still waiting for the payment. */
        data object PendingInPlay : Submission

        data object Updating : Submission

        /** The purchase exists but couldn't be attached to this installation yet. */
        data object NotAttached : Submission

        /** The server refused the purchase. */
        data object Rejected : Submission
    }
}

sealed interface SubscribeResult {
    /** The store's purchase sheet opened; the outcome arrives through [SubscriptionState]. */
    data object Launched : SubscribeResult

    data object Canceled : SubscribeResult

    data object AlreadyOwned : SubscribeResult

    data object Unavailable : SubscribeResult

    data class Failed(val cause: Throwable?) : SubscribeResult
}

sealed interface RestoreResult {
    data object Found : RestoreResult

    data object NothingFound : RestoreResult

    data object Unreachable : RestoreResult
}
