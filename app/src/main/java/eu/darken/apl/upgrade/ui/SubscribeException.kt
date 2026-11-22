package eu.darken.apl.upgrade.ui

import android.content.Context
import eu.darken.apl.R
import eu.darken.apl.common.ca.toCaString
import eu.darken.apl.common.error.HasLocalizedError
import eu.darken.apl.common.error.LocalizedError
import eu.darken.apl.upgrade.subscription.SubscribeResult

/** Google Play's purchase sheet did not open. */
class SubscribeException(val result: SubscribeResult) :
    RuntimeException("Subscribing failed: $result", (result as? SubscribeResult.Failed)?.cause), HasLocalizedError {

    override fun getLocalizedError(context: Context) = LocalizedError(
        throwable = this,
        label = R.string.upgrade_subscription_error_title.toCaString(),
        description = when (result) {
            SubscribeResult.Unavailable -> R.string.upgrade_subscription_unavailable_msg.toCaString()
            else -> R.string.upgrade_subscription_failed_msg.toCaString()
        },
    )
}
