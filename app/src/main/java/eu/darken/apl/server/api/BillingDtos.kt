package eu.darken.apl.server.api

import kotlinx.serialization.Serializable

@Serializable
data class SubscriptionRequest(
    val productId: String,
    val purchaseToken: String,
) {
    // The purchase token is a credential, logging this object must not reveal it
    override fun toString(): String = "SubscriptionRequest($productId, purchaseToken=${purchaseToken.length} chars)"
}

/**
 * [state] stays a plain string, so a value this build does not know decodes instead of failing.
 * [expiresAt] is in epoch milliseconds.
 */
@Serializable
data class SubscriptionResponse(
    val state: String,
    val granted: Boolean,
    val expiresAt: Long? = null,
    val attached: Boolean,
)
