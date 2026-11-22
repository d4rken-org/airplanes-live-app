package eu.darken.apl.upgrade.subscription.play

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesResponseListener
import com.android.billingclient.api.PurchasesUpdatedListener
import eu.darken.apl.common.debug.logging.Logging
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelper.coroutine.TestDispatcherProvider

/** Real `Purchase` objects need `org.json`, hence Robolectric. */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class PlayBillingClientLoggingTest {

    private val logged = CapturingLogger()

    private var queryResult: BillingResult = result(BillingResponseCode.OK)
    private lateinit var purchasesUpdatedListener: PurchasesUpdatedListener
    private val client = mockk<BillingClient>().apply {
        every { isReady } returns true
        every { queryPurchasesAsync(any(), any()) } answers {
            secondArg<PurchasesResponseListener>().onQueryPurchasesResponse(queryResult, listOf(sentinelPurchase()))
        }
    }
    private val billing = PlayBillingClient(
        clientFactory = { listener ->
            purchasesUpdatedListener = listener
            client
        },
        dispatcherProvider = TestDispatcherProvider(),
    )

    @Before
    fun setup() {
        Logging.clearAll()
        Logging.install(logged)
    }

    @After
    fun teardown() {
        Logging.clearAll()
    }

    @Test
    fun `purchase callbacks never log the token`() = runTest {
        billing.connect() shouldBe PlayConnection.Ready

        purchasesUpdatedListener.onPurchasesUpdated(result(BillingResponseCode.OK), listOf(sentinelPurchase()))
        purchasesUpdatedListener.onPurchasesUpdated(result(BillingResponseCode.ERROR), listOf(sentinelPurchase()))
        val updates = billing.purchaseUpdates.take(2).toList()

        (updates[0] as PurchaseUpdate.Purchases).purchases.single().token shouldBe TOKEN
        updates[1] shouldBe PurchaseUpdate.Failed(BillingResponseCode.ERROR)
        logged.lines.filter { "onPurchasesUpdated" in it && PlaySubscriptionProduct.PRODUCT_ID in it }.shouldNotBeEmpty()
        logged.assertNoSecrets()
    }

    @Test
    fun `purchase queries never log the token`() = runTest {
        val query = billing.queryPurchases() as PurchaseQuery.Success

        query.purchases.single().token shouldBe TOKEN
        logged.lines.filter { "queryPurchases" in it && PlaySubscriptionProduct.PRODUCT_ID in it }.shouldNotBeEmpty()
        logged.assertNoSecrets()
    }

    @Test
    fun `a failed purchase query is a failure even with purchases attached`() = runTest {
        queryResult = result(BillingResponseCode.SERVICE_UNAVAILABLE)

        billing.queryPurchases() shouldBe PurchaseQuery.Failed(BillingResponseCode.SERVICE_UNAVAILABLE)
        logged.lines.filter { "queryPurchases" in it }.shouldNotBeEmpty()
        logged.assertNoSecrets()
    }

    private class CapturingLogger : Logging.Logger {
        val lines = mutableListOf<String>()

        override fun log(priority: Logging.Priority, tag: String, message: String, metaData: Map<String, Any>?) {
            synchronized(lines) { lines.add("$tag $message") }
        }

        fun assertNoSecrets() = synchronized(lines) {
            lines.forEach { line ->
                line shouldNotContain TOKEN
                line shouldNotContain ORDER_ID
                line shouldNotContain SIGNATURE
            }
        }
    }

    companion object {
        private const val TOKEN = "PURCHASE-TOKEN-SENTINEL"
        private const val ORDER_ID = "GPA.ORDER-SENTINEL"
        private const val SIGNATURE = "SIGNATURE-SENTINEL"

        private fun result(code: Int): BillingResult = BillingResult.newBuilder()
            .setResponseCode(code)
            .setDebugMessage("debug-$code")
            .build()

        private fun sentinelPurchase() = Purchase(
            """
            {
              "orderId": "$ORDER_ID",
              "packageName": "eu.darken.apl",
              "productId": "${PlaySubscriptionProduct.PRODUCT_ID}",
              "purchaseTime": 1700000000000,
              "purchaseState": 0,
              "purchaseToken": "$TOKEN",
              "autoRenewing": true,
              "acknowledged": false
            }
            """.trimIndent(),
            SIGNATURE,
        )
    }
}
