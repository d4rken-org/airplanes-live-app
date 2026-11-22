package eu.darken.apl.upgrade.subscription.play

import eu.darken.apl.upgrade.subscription.play.PlayPricingPhase.Recurrence
import eu.darken.apl.upgrade.subscription.play.PlaySubscriptionProduct.BASE_PLAN_ID
import eu.darken.apl.upgrade.subscription.play.PlaySubscriptionProduct.TRIAL_OFFER_ID
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.time.Period

class PlayOfferTest : BaseTest() {

    private val monthly = PlayPricingPhase(
        formattedPrice = "€2.99",
        priceMicros = 2_990_000L,
        billingPeriod = "P1M",
        billingCycleCount = 0,
        recurrence = Recurrence.INFINITE,
    )
    private val freeWeek = PlayPricingPhase(
        formattedPrice = "Free",
        priceMicros = 0L,
        billingPeriod = "P1W",
        billingCycleCount = 1,
        recurrence = Recurrence.FINITE,
    )

    private val basePlan = PlayOfferEntry(
        basePlanId = BASE_PLAN_ID,
        offerId = null,
        offerToken = "offer-token-base",
        phases = listOf(monthly),
    )
    private val trial = PlayOfferEntry(
        basePlanId = BASE_PLAN_ID,
        offerId = TRIAL_OFFER_ID,
        offerToken = "offer-token-trial",
        phases = listOf(freeWeek, monthly),
    )
    private val promo = PlayOfferEntry(
        basePlanId = BASE_PLAN_ID,
        offerId = "upgrade-pro-monthly-promo",
        offerToken = "offer-token-promo",
        phases = listOf(monthly.copy(formattedPrice = "€0.99", priceMicros = 990_000L, billingCycleCount = 3, recurrence = Recurrence.FINITE), monthly),
    )
    private val yearlyPlan = PlayOfferEntry(
        basePlanId = "upgrade-pro-yearly",
        offerId = null,
        offerToken = "offer-token-yearly",
        phases = listOf(monthly.copy(formattedPrice = "€29.99", priceMicros = 29_990_000L, billingPeriod = "P1Y")),
    )

    private val trialOffer = PlayOffer(
        basePlanId = BASE_PLAN_ID,
        offerId = TRIAL_OFFER_ID,
        offerToken = "offer-token-trial",
        formattedPrice = "€2.99",
        priceMicros = 2_990_000L,
        billingPeriod = Period.ofMonths(1),
        trialPeriod = Period.ofDays(7),
    )
    private val basePlanOffer = PlayOffer(
        basePlanId = BASE_PLAN_ID,
        offerId = null,
        offerToken = "offer-token-base",
        formattedPrice = "€2.99",
        priceMicros = 2_990_000L,
        billingPeriod = Period.ofMonths(1),
        trialPeriod = null,
    )

    @Test
    fun `a returned trial offer is sold with its trial and the price after it`() {
        selectOffer(listOf(basePlan, trial)) shouldBe OfferQuery.Found(trialOffer)
    }

    @Test
    fun `without the trial offer the base plan is sold without a trial`() {
        selectOffer(listOf(basePlan)) shouldBe OfferQuery.Found(basePlanOffer)
    }

    @Test
    fun `a missing base plan is missing, even when the trial offer came back`() {
        selectOffer(listOf(trial)) shouldBe OfferQuery.Missing
        selectOffer(listOf(yearlyPlan)) shouldBe OfferQuery.Missing
        selectOffer(emptyList()) shouldBe OfferQuery.Missing
    }

    @Test
    fun `a missing product is missing`() {
        selectOffer(null) shouldBe OfferQuery.Missing
    }

    @Test
    fun `unrelated offers and base plans are ignored`() {
        selectOffer(listOf(yearlyPlan, promo, basePlan, trial)) shouldBe OfferQuery.Found(trialOffer)
        selectOffer(listOf(promo, yearlyPlan, basePlan)) shouldBe OfferQuery.Found(basePlanOffer)
    }

    @Test
    fun `a trial over several cycles lasts all of them`() {
        val twoWeeks = trial.copy(phases = listOf(freeWeek.copy(billingCycleCount = 2), monthly))

        selectOffer(listOf(basePlan, twoWeeks)) shouldBe OfferQuery.Found(trialOffer.copy(trialPeriod = Period.ofDays(14)))
    }
}
