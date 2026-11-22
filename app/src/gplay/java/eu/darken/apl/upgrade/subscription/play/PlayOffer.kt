package eu.darken.apl.upgrade.subscription.play

import com.android.billingclient.api.ProductDetails
import java.time.Period

/** The offer to sell, picked by [selectOffer]. */
data class PlayOffer(
    val basePlanId: String,
    val offerId: String?,
    val offerToken: String,
    /** Charged per [billingPeriod] once the trial ends. */
    val formattedPrice: String,
    val priceMicros: Long,
    val billingPeriod: Period,
    val trialPeriod: Period?,
) {
    override fun toString(): String =
        "PlayOffer($basePlanId/$offerId, price=$formattedPrice per $billingPeriod, trial=$trialPeriod)"
}

/** Mirror of `ProductDetails.SubscriptionOfferDetails`. */
data class PlayOfferEntry(
    val basePlanId: String,
    /** Null for the base plan's own entry. */
    val offerId: String?,
    val offerToken: String,
    val phases: List<PlayPricingPhase>,
) {
    override fun toString(): String = "PlayOfferEntry($basePlanId/$offerId, phases=$phases)"
}

/** Mirror of `ProductDetails.PricingPhase`. */
data class PlayPricingPhase(
    val formattedPrice: String,
    val priceMicros: Long,
    /** ISO 8601, e.g. `P1M`, `P1W`, `P7D`. */
    val billingPeriod: String,
    val billingCycleCount: Int,
    val recurrence: Recurrence,
) {
    enum class Recurrence {
        INFINITE,
        FINITE,
        NON_RECURRING,
        UNKNOWN,
    }
}

/**
 * [entries] null means Play returned no such product. Within [PlaySubscriptionProduct.BASE_PLAN_ID],
 * the trial offer wins when Play returned it for this account, else the base plan's own entry.
 *
 * Trial offer `[free P7D x1, 2.99 P1M infinite]` gives price 2.99, period P1M, trial P7D.
 */
fun selectOffer(entries: List<PlayOfferEntry>?): OfferQuery {
    if (entries == null) return OfferQuery.Missing
    val ofBasePlan = entries.filter { it.basePlanId == PlaySubscriptionProduct.BASE_PLAN_ID }
    // Exactly one, a duplicate entry leaves no way to tell which price Play meant
    val basePlan = ofBasePlan.singleOrNull { it.offerId == null } ?: return OfferQuery.Missing
    val trial = ofBasePlan.singleOrNull { it.offerId == PlaySubscriptionProduct.TRIAL_OFFER_ID }
    val chosen = trial ?: basePlan

    val recurring = chosen.phases.lastOrNull { it.recurrence == PlayPricingPhase.Recurrence.INFINITE }
        ?: chosen.phases.lastOrNull()
        ?: return OfferQuery.Missing
    if (recurring.priceMicros <= 0L) return OfferQuery.Missing
    val billingPeriod = recurring.billingPeriod.toPeriodOrNull() ?: return OfferQuery.Missing

    val free = chosen.phases.firstOrNull { it.priceMicros == 0L }
    val trialPeriod = free?.let { phase ->
        val period = phase.billingPeriod.toPeriodOrNull() ?: return OfferQuery.Missing
        if (phase.billingCycleCount > 1) period.multipliedBy(phase.billingCycleCount) else period
    }

    return OfferQuery.Found(
        PlayOffer(
            basePlanId = chosen.basePlanId,
            offerId = chosen.offerId,
            offerToken = chosen.offerToken,
            formattedPrice = recurring.formattedPrice,
            priceMicros = recurring.priceMicros,
            billingPeriod = billingPeriod,
            trialPeriod = trialPeriod,
        )
    )
}

private fun String.toPeriodOrNull(): Period? = runCatching { Period.parse(this) }.getOrNull()

internal fun ProductDetails.SubscriptionOfferDetails.toOfferEntry() = PlayOfferEntry(
    basePlanId = basePlanId,
    offerId = offerId,
    offerToken = offerToken,
    phases = pricingPhases.pricingPhaseList.map { phase ->
        PlayPricingPhase(
            formattedPrice = phase.formattedPrice,
            priceMicros = phase.priceAmountMicros,
            billingPeriod = phase.billingPeriod,
            billingCycleCount = phase.billingCycleCount,
            recurrence = when (phase.recurrenceMode) {
                ProductDetails.RecurrenceMode.INFINITE_RECURRING -> PlayPricingPhase.Recurrence.INFINITE
                ProductDetails.RecurrenceMode.FINITE_RECURRING -> PlayPricingPhase.Recurrence.FINITE
                ProductDetails.RecurrenceMode.NON_RECURRING -> PlayPricingPhase.Recurrence.NON_RECURRING
                else -> PlayPricingPhase.Recurrence.UNKNOWN
            },
        )
    },
)
