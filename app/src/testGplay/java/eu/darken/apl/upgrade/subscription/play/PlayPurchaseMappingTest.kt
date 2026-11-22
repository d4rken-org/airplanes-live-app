package eu.darken.apl.upgrade.subscription.play

import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.Purchase.PurchaseState
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class PlayPurchaseMappingTest : BaseTest() {

    private fun purchase(
        state: Int,
        token: String = "token-1",
        suspended: Boolean = false,
        autoRenewing: Boolean = true,
    ) = mockk<Purchase>().apply {
        every { purchaseToken } returns token
        every { products } returns listOf(PlaySubscriptionProduct.PRODUCT_ID)
        every { purchaseState } returns state
        every { isSuspended } returns suspended
        every { isAutoRenewing } returns autoRenewing
    }

    private fun result(code: Int) = mockk<BillingResult>().apply {
        every { responseCode } returns code
        every { debugMessage } returns ""
    }

    private fun mapped(
        state: PlayPurchase.State,
        token: String = "token-1",
        suspended: Boolean = false,
        autoRenewing: Boolean = true,
    ) = PlayPurchase(
        token = token,
        products = listOf(PlaySubscriptionProduct.PRODUCT_ID),
        state = state,
        isSuspended = suspended,
        isAutoRenewing = autoRenewing,
    )

    @Test
    fun `purchase states map to pending, purchased or other`() {
        purchase(PurchaseState.PENDING).toPlayPurchase() shouldBe mapped(PlayPurchase.State.PENDING)
        purchase(PurchaseState.PURCHASED).toPlayPurchase() shouldBe mapped(PlayPurchase.State.PURCHASED)
        purchase(PurchaseState.UNSPECIFIED_STATE).toPlayPurchase() shouldBe mapped(PlayPurchase.State.OTHER)
    }

    @Test
    fun `suspended and auto renewing flags are kept`() {
        purchase(PurchaseState.PURCHASED, suspended = true, autoRenewing = false).toPlayPurchase() shouldBe
            mapped(PlayPurchase.State.PURCHASED, suspended = true, autoRenewing = false)
    }

    @Test
    fun `a successful query returns its purchases`() {
        PlayBillingClient.mapPurchaseQuery(
            result(BillingResponseCode.OK),
            listOf(purchase(PurchaseState.PURCHASED), purchase(PurchaseState.PENDING, token = "token-2")),
        ) shouldBe PurchaseQuery.Success(
            listOf(mapped(PlayPurchase.State.PURCHASED), mapped(PlayPurchase.State.PENDING, token = "token-2"))
        )
        PlayBillingClient.mapPurchaseQuery(result(BillingResponseCode.OK), emptyList()) shouldBe
            PurchaseQuery.Success(emptyList())
    }

    @Test
    fun `a failed query is a failure, never an empty success`() {
        PlayBillingClient.mapPurchaseQuery(result(BillingResponseCode.SERVICE_DISCONNECTED), emptyList()) shouldBe
            PurchaseQuery.Failed(BillingResponseCode.SERVICE_DISCONNECTED)
        PlayBillingClient.mapPurchaseQuery(result(BillingResponseCode.ERROR), listOf(purchase(PurchaseState.PURCHASED))) shouldBe
            PurchaseQuery.Failed(BillingResponseCode.ERROR)
        PlayBillingClient.mapPurchaseQuery(result(BillingResponseCode.OK), null) shouldBe
            PurchaseQuery.Failed(BillingResponseCode.ERROR)
    }

    @Test
    fun `purchase updates map by response code`() {
        PlayBillingClient.mapPurchaseUpdate(result(BillingResponseCode.OK), listOf(purchase(PurchaseState.PENDING))) shouldBe
            PurchaseUpdate.Purchases(listOf(mapped(PlayPurchase.State.PENDING)))
        PlayBillingClient.mapPurchaseUpdate(result(BillingResponseCode.USER_CANCELED), null) shouldBe PurchaseUpdate.Canceled
        PlayBillingClient.mapPurchaseUpdate(result(BillingResponseCode.ITEM_ALREADY_OWNED), null) shouldBe
            PurchaseUpdate.AlreadyOwned
        PlayBillingClient.mapPurchaseUpdate(result(BillingResponseCode.NETWORK_ERROR), null) shouldBe
            PurchaseUpdate.Failed(BillingResponseCode.NETWORK_ERROR)
    }

    @Test
    fun `launch results map by response code`() {
        PlayBillingClient.launchOutcomeFor(BillingResponseCode.OK) shouldBe LaunchOutcome.Launched
        PlayBillingClient.launchOutcomeFor(BillingResponseCode.USER_CANCELED) shouldBe LaunchOutcome.Canceled
        PlayBillingClient.launchOutcomeFor(BillingResponseCode.ITEM_ALREADY_OWNED) shouldBe LaunchOutcome.AlreadyOwned
        PlayBillingClient.launchOutcomeFor(BillingResponseCode.BILLING_UNAVAILABLE) shouldBe LaunchOutcome.Unavailable
        PlayBillingClient.launchOutcomeFor(BillingResponseCode.ERROR) shouldBe LaunchOutcome.Failed(BillingResponseCode.ERROR)
    }

    @Test
    fun `a purchase prints without its token`() {
        val printed = mapped(PlayPurchase.State.PURCHASED, token = "PURCHASE-TOKEN-SENTINEL").toString()

        printed shouldNotContain "PURCHASE-TOKEN-SENTINEL"
        printed shouldContain PlaySubscriptionProduct.PRODUCT_ID
    }
}
