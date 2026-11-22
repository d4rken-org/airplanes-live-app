package eu.darken.apl.upgrade.subscription.play

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.InAppMessageParams
import com.android.billingclient.api.InAppMessageResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryProductDetailsResult
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.UnfetchedProduct
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.Logging.Priority.INFO
import eu.darken.apl.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One [BillingClient] for the app's lifetime. Play's `Purchase` and `ProductDetails` never leave this
 * class and are never logged as is: their `toString()` carries the purchase token, the order ID or
 * Play's raw response.
 */
@Singleton
class PlayBillingClient internal constructor(
    clientFactory: (PurchasesUpdatedListener) -> BillingClient,
    private val dispatcherProvider: DispatcherProvider,
) : PlayBilling {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        dispatcherProvider: DispatcherProvider,
    ) : this(
        clientFactory = { listener ->
            BillingClient.newBuilder(context)
                .setListener(listener)
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .enableAutoServiceReconnection()
                .build()
        },
        dispatcherProvider = dispatcherProvider,
    )

    // UNLIMITED: a dropped event could be the only notice of a purchase
    private val updates = Channel<PurchaseUpdate>(Channel.UNLIMITED)
    override val purchaseUpdates: Flow<PurchaseUpdate> = updates.receiveAsFlow()

    private val purchasesUpdatedListener = PurchasesUpdatedListener { result, purchases ->
        val update = mapPurchaseUpdate(result, purchases)
        log(TAG, if (update is PurchaseUpdate.Failed) WARN else INFO) {
            "onPurchasesUpdated(${result.printed()}): $update"
        }
        updates.trySend(update)
    }

    private val client: BillingClient by lazy { clientFactory(purchasesUpdatedListener) }

    private val _connection = MutableStateFlow<PlayConnection>(PlayConnection.Disconnected)
    override val connection: StateFlow<PlayConnection> = _connection.asStateFlow()

    private val connectLock = Mutex()

    override suspend fun connect(): PlayConnection = connectLock.withLock {
        try {
            if (client.isReady) {
                _connection.value = PlayConnection.Ready
                return@withLock PlayConnection.Ready
            }
            _connection.value = PlayConnection.Connecting
            val setup = CompletableDeferred<PlayConnection>()
            log(TAG, VERBOSE) { "startConnection()" }
            // Play may call this listener again later, e.g. after it reconnected on its own
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    val state = when (result.responseCode) {
                        BillingResponseCode.OK -> PlayConnection.Ready
                        else -> PlayConnection.Unavailable(result.responseCode)
                    }
                    log(TAG, if (state == PlayConnection.Ready) VERBOSE else WARN) {
                        "onBillingSetupFinished(${result.printed()})"
                    }
                    _connection.value = state
                    setup.complete(state)
                }

                override fun onBillingServiceDisconnected() {
                    log(TAG, WARN) { "onBillingServiceDisconnected()" }
                    _connection.value = PlayConnection.Disconnected
                    setup.complete(PlayConnection.Disconnected)
                }
            })
            withTimeoutOrNull(SETUP_TIMEOUT_MS) { setup.await() } ?: run {
                log(TAG, WARN) { "connect(): Play didn't answer within ${SETUP_TIMEOUT_MS}ms" }
                PlayConnection.Unavailable(BillingResponseCode.SERVICE_TIMEOUT).also { _connection.value = it }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(TAG, WARN) { "connect() failed: ${e.asLog()}" }
            PlayConnection.Unavailable(BillingResponseCode.ERROR).also { _connection.value = it }
        }
    }

    override suspend fun queryPurchases(): PurchaseQuery = guarded("queryPurchases", PurchaseQuery::Failed) {
        val connection = connect()
        if (connection != PlayConnection.Ready) return@guarded PurchaseQuery.Failed(connection.failureCode())

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(PlaySubscriptionProduct.PRODUCT_TYPE)
            .includeSuspendedSubscriptions(true)
            .build()
        val (result, purchases) = awaitPlay<Pair<BillingResult, List<Purchase>?>> { answer ->
            client.queryPurchasesAsync(params) { result, purchases -> answer(result to purchases) }
        } ?: return@guarded PurchaseQuery.Failed(BillingResponseCode.SERVICE_TIMEOUT)

        mapPurchaseQuery(result, purchases).also {
            log(TAG, if (it is PurchaseQuery.Failed) WARN else INFO) { "queryPurchases(${result.printed()}): $it" }
        }
    }

    override suspend fun queryOffer(): OfferQuery = guarded("queryOffer", OfferQuery::Failed) {
        loadOffer().query
    }

    override suspend fun launch(activity: Activity): LaunchOutcome = guarded("launch", LaunchOutcome::Failed) {
        val loaded = loadOffer()
        val offer = when (val query = loaded.query) {
            is OfferQuery.Found -> query.offer
            OfferQuery.Missing -> return@guarded LaunchOutcome.Unavailable
            is OfferQuery.Failed -> return@guarded launchOutcomeFor(query.code)
        }
        val details = loaded.details ?: return@guarded LaunchOutcome.Unavailable

        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(offer.offerToken)
                        .build()
                )
            )
            .build()
        // launchBillingFlow has to run on the main thread
        val result = withContext(dispatcherProvider.Main) { client.launchBillingFlow(activity, params) }
        launchOutcomeFor(result.responseCode).also {
            log(TAG, INFO) { "launch($offer): ${result.printed()} -> $it" }
        }
    }

    override suspend fun showInAppMessages(
        activity: Activity,
        onSubscriptionStatusUpdated: () -> Unit,
    ): Unit = guarded("showInAppMessages", onError = {}) {
        val connection = connect()
        if (connection != PlayConnection.Ready) {
            log(TAG) { "showInAppMessages(): skipped, $connection" }
            return@guarded
        }
        val params = InAppMessageParams.newBuilder()
            .addInAppMessageCategoryToShow(InAppMessageParams.InAppMessageCategoryId.TRANSACTIONAL)
            .build()
        val result = withContext(dispatcherProvider.Main) {
            client.showInAppMessages(activity, params) { messageResult ->
                // The result also carries a purchase token, only the code is logged
                log(TAG, INFO) { "onInAppMessageResponse(code=${messageResult.responseCode})" }
                if (messageResult.responseCode == InAppMessageResult.InAppMessageResponseCode.SUBSCRIPTION_STATUS_UPDATED) {
                    onSubscriptionStatusUpdated()
                }
            }
        }
        log(TAG) { "showInAppMessages(): ${result.printed()}" }
    }

    private data class LoadedOffer(val query: OfferQuery, val details: ProductDetails? = null)

    private suspend fun loadOffer(): LoadedOffer {
        val connection = connect()
        if (connection != PlayConnection.Ready) return LoadedOffer(OfferQuery.Failed(connection.failureCode()))

        val support = client.isFeatureSupported(BillingClient.FeatureType.SUBSCRIPTIONS)
        when (support.responseCode) {
            BillingResponseCode.OK -> Unit
            BillingResponseCode.FEATURE_NOT_SUPPORTED -> {
                log(TAG, WARN) { "loadOffer(): subscriptions unsupported" }
                return LoadedOffer(OfferQuery.Missing)
            }

            else -> return LoadedOffer(OfferQuery.Failed(support.responseCode))
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PlaySubscriptionProduct.PRODUCT_ID)
                        .setProductType(PlaySubscriptionProduct.PRODUCT_TYPE)
                        .build()
                )
            )
            .build()
        val (result, queryResult) = awaitPlay<Pair<BillingResult, QueryProductDetailsResult?>> { answer ->
            client.queryProductDetailsAsync(params) { result, queryResult -> answer(result to queryResult) }
        } ?: return LoadedOffer(OfferQuery.Failed(BillingResponseCode.SERVICE_TIMEOUT))

        when (result.responseCode) {
            BillingResponseCode.OK -> Unit
            BillingResponseCode.ITEM_UNAVAILABLE, BillingResponseCode.FEATURE_NOT_SUPPORTED -> {
                log(TAG, WARN) { "loadOffer(): ${result.printed()}" }
                return LoadedOffer(OfferQuery.Missing)
            }

            else -> {
                log(TAG, WARN) { "loadOffer(): ${result.printed()}" }
                return LoadedOffer(OfferQuery.Failed(result.responseCode))
            }
        }

        // Exactly one, a duplicate leaves no way to tell which one Play meant
        val details = queryResult?.productDetailsList.orEmpty().singleOrNull {
            it.productId == PlaySubscriptionProduct.PRODUCT_ID && it.productType == PlaySubscriptionProduct.PRODUCT_TYPE
        }
        val entries = details?.subscriptionOfferDetails?.map { it.toOfferEntry() }
        val selection = selectOffer(entries)
        log(TAG, if (selection is OfferQuery.Found) INFO else WARN) {
            val unfetched = queryResult?.unfetchedProductList.orEmpty().printed()
            "loadOffer(${result.printed()}): entries=$entries, unfetched=$unfetched -> $selection"
        }
        return LoadedOffer(selection, details.takeIf { selection is OfferQuery.Found })
    }

    private suspend fun <T> awaitPlay(call: (answer: (T) -> Unit) -> Unit): T? {
        val answer = CompletableDeferred<T>()
        call { answer.complete(it) }
        return withTimeoutOrNull(CALL_TIMEOUT_MS) { answer.await() }
    }

    // Play can throw from its calls, e.g. when its service died; that is one more failed attempt
    private inline fun <T> guarded(name: String, onError: (Int) -> T, block: () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log(TAG, WARN) { "$name() failed: ${e.asLog()}" }
        onError(BillingResponseCode.ERROR)
    }

    companion object {
        private val TAG = logTag("Upgrade", "Subscription", "Play", "Client")

        private const val SETUP_TIMEOUT_MS = 30_000L
        private const val CALL_TIMEOUT_MS = 30_000L

        internal fun mapPurchaseUpdate(result: BillingResult, purchases: List<Purchase>?): PurchaseUpdate =
            when (result.responseCode) {
                BillingResponseCode.OK -> PurchaseUpdate.Purchases(purchases.orEmpty().map { it.toPlayPurchase() })
                BillingResponseCode.USER_CANCELED -> PurchaseUpdate.Canceled
                BillingResponseCode.ITEM_ALREADY_OWNED -> PurchaseUpdate.AlreadyOwned
                else -> PurchaseUpdate.Failed(result.responseCode)
            }

        // A null list on success proves nothing about absence
        internal fun mapPurchaseQuery(result: BillingResult, purchases: List<Purchase>?): PurchaseQuery = when {
            result.responseCode != BillingResponseCode.OK -> PurchaseQuery.Failed(result.responseCode)
            purchases == null -> PurchaseQuery.Failed(BillingResponseCode.ERROR)
            else -> PurchaseQuery.Success(purchases.map { it.toPlayPurchase() })
        }

        internal fun launchOutcomeFor(code: Int): LaunchOutcome = when (code) {
            BillingResponseCode.OK -> LaunchOutcome.Launched
            BillingResponseCode.USER_CANCELED -> LaunchOutcome.Canceled
            BillingResponseCode.ITEM_ALREADY_OWNED -> LaunchOutcome.AlreadyOwned
            BillingResponseCode.BILLING_UNAVAILABLE,
            BillingResponseCode.ITEM_UNAVAILABLE,
            BillingResponseCode.FEATURE_NOT_SUPPORTED -> LaunchOutcome.Unavailable

            else -> LaunchOutcome.Failed(code)
        }

        private fun PlayConnection.failureCode(): Int = when (this) {
            is PlayConnection.Unavailable -> code
            else -> BillingResponseCode.SERVICE_DISCONNECTED
        }

        private fun BillingResult.printed(): String = "code=$responseCode, message=$debugMessage"

        private fun Collection<UnfetchedProduct>.printed(): String =
            joinToString(prefix = "[", postfix = "]") { "${it.productId}(${it.productType})=${it.statusCode}" }
    }
}
