package eu.darken.apl.upgrade.subscription.play

import com.android.billingclient.api.BillingClient

/** The Pro subscription as configured in the Play Console. */
object PlaySubscriptionProduct {
    const val PRODUCT_ID = "upgrade.pro"
    const val BASE_PLAN_ID = "upgrade-pro-monthly"
    const val TRIAL_OFFER_ID = "upgrade-pro-monthly-trial"
    const val PRODUCT_TYPE = BillingClient.ProductType.SUBS
}
