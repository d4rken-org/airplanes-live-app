package eu.darken.apl.upgrade.subscription.play

import com.android.billingclient.api.Purchase
import java.security.MessageDigest

/**
 * A Play purchase without Play's type. [toString] is safe to log: it never contains [token].
 * `Purchase.toString()` and `Purchase.originalJson` carry the token and the order ID, and debug logs
 * leave the device.
 */
data class PlayPurchase(
    val token: String,
    val products: List<String>,
    val state: State,
    val isSuspended: Boolean,
    val isAutoRenewing: Boolean,
) {

    enum class State {
        PENDING,
        PURCHASED,
        OTHER,
    }

    /** Lets log lines about the same purchase be matched up without exposing the token. */
    val tokenHash: String
        get() = MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(Charsets.UTF_8))
            .take(4)
            .joinToString("") { "%02x".format(it) }

    override fun toString(): String =
        "PlayPurchase(products=$products, state=$state, suspended=$isSuspended, " +
            "autoRenewing=$isAutoRenewing, token=#$tokenHash)"
}

internal fun Purchase.toPlayPurchase(): PlayPurchase = PlayPurchase(
    token = purchaseToken,
    products = products.toList(),
    state = when (purchaseState) {
        Purchase.PurchaseState.PURCHASED -> PlayPurchase.State.PURCHASED
        Purchase.PurchaseState.PENDING -> PlayPurchase.State.PENDING
        else -> PlayPurchase.State.OTHER
    },
    isSuspended = isSuspended,
    isAutoRenewing = isAutoRenewing,
)
