package eu.darken.apl.upgrade.subscription

import android.app.Activity
import android.net.Uri
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import eu.darken.apl.common.BuildConfigWrap
import eu.darken.apl.common.coroutine.AppScope
import eu.darken.apl.common.debug.logging.Logging.Priority.INFO
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.server.PrivacyAcceptance
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.access.AccessState
import eu.darken.apl.server.session.SessionManager
import eu.darken.apl.server.session.SessionState
import eu.darken.apl.upgrade.subscription.SubscriptionState.Offer
import eu.darken.apl.upgrade.subscription.SubscriptionState.Ownership
import eu.darken.apl.upgrade.subscription.SubscriptionState.Submission
import eu.darken.apl.upgrade.subscription.play.LaunchOutcome
import eu.darken.apl.upgrade.subscription.play.OfferQuery
import eu.darken.apl.upgrade.subscription.play.PlayBilling
import eu.darken.apl.upgrade.subscription.play.PlayConnection
import eu.darken.apl.upgrade.subscription.play.PlayPurchase
import eu.darken.apl.upgrade.subscription.play.PlaySubscriptionProduct
import eu.darken.apl.upgrade.subscription.play.PurchaseQuery
import eu.darken.apl.upgrade.subscription.play.PurchaseUpdate
import eu.darken.apl.upgrade.subscription.submit.SubscriptionSubmissions
import eu.darken.apl.upgrade.subscription.submit.SubscriptionTokenRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps Play's view of the Pro subscription and hands purchases to [SubscriptionSubmissions]. Pro
 * itself comes only from the server's access policy, nothing here feeds it.
 */
@Singleton
class SubscriptionBillingGplay internal constructor(
    private val appScope: CoroutineScope,
    private val playBilling: PlayBilling,
    private val submissions: SubscriptionSubmissions,
    private val accessRepo: AccessRepo,
    private val sessionManager: SessionManager,
    private val privacyAcceptance: PrivacyAcceptance,
    private val observeForeground: (onForeground: () -> Unit) -> Unit,
    private val packageName: String,
) : SubscriptionBilling {

    @Inject
    constructor(
        @AppScope appScope: CoroutineScope,
        playBilling: PlayBilling,
        submissions: SubscriptionSubmissions,
        accessRepo: AccessRepo,
        sessionManager: SessionManager,
        privacyAcceptance: PrivacyAcceptance,
    ) : this(
        appScope = appScope,
        playBilling = playBilling,
        submissions = submissions,
        accessRepo = accessRepo,
        sessionManager = sessionManager,
        privacyAcceptance = privacyAcceptance,
        observeForeground = { onForeground ->
            ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = onForeground()
            })
        },
        packageName = BuildConfigWrap.APPLICATION_ID,
    )

    private val offer = MutableStateFlow<Offer>(Offer.Loading)
    private val ownership = MutableStateFlow<Ownership>(Ownership.Unknown)

    /** Hashes of the Pro tokens Play reported since its last successful purchase query, null before one. */
    private val reportedTokens = MutableStateFlow<Set<String>?>(null)
    private val updating = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val currentRecords: Flow<List<SubscriptionTokenRecord>> = sessionManager.state
        .map { (it as? SessionState.Active)?.installationId }
        .distinctUntilChanged()
        .flatMapLatest { installationId ->
            if (installationId == null) flowOf(emptyList()) else submissions.records(installationId)
        }

    override val state: Flow<SubscriptionState> = combine(
        offer,
        ownership,
        updating,
        reportedTokens,
        currentRecords,
    ) { offer, ownership, updating, reported, records ->
        SubscriptionState(
            supported = true,
            offer = offer,
            ownership = ownership,
            submission = submissionOf(updating, records, reported),
        )
    }.distinctUntilChanged()

    override val manageUri: Uri? by lazy { Uri.parse(manageUrl(packageName)) }

    /** Purchase sheets, Play queries and submission requests run one at a time. */
    private val operationLock = Mutex()

    private class Pending(val reasons: List<String>, val requestAll: Boolean, val done: CompletableDeferred<Unit>)

    /** Guarded by `this`. */
    private var pending: Pending? = null

    /** Guarded by `this`. */
    private var runner: Job? = null

    @Volatile private var foregroundSession = 0L

    /** Guarded by [operationLock]. The foreground session each token was last requested in. */
    private val requestedInSession = mutableMapOf<String, Long>()

    private var isInit = false

    override fun setup() {
        if (isInit) return
        isInit = true
        log(TAG) { "setup()" }

        appScope.launch {
            playBilling.purchaseUpdates.collect { onPurchaseUpdate(it) }
        }

        appScope.launch {
            var first = true
            submissions.updating.collect { value ->
                updating.value = value
                if (first && value) {
                    log(TAG, INFO) { "A submission was waiting for its access policy, refreshing" }
                    appScope.launch { refreshAccess("subscription-recovery") }
                }
                first = false
            }
        }

        // A reinstall's first run reaches this only once onboarding and enrollment are done
        appScope.launch {
            val reconciled = mutableSetOf<String>()
            combine(sessionManager.state, accessRepo.state) { session, access ->
                (session as? SessionState.Active)?.installationId?.takeIf { it == access?.installationId }
            }
                .filterNotNull()
                .collect { installationId ->
                    if (reconciled.add(installationId)) reconcile("access-ready")
                }
        }

        observeForeground {
            foregroundSession++
            reconcile("foreground")
        }
    }

    override fun reconcile(reason: String) {
        enqueue(reason, requestAll = false)
    }

    override suspend fun restore() {
        enqueue("restore", requestAll = true).await()
    }

    override suspend fun subscribe(activity: Activity): SubscribeResult {
        val outcome = operationLock.withLock { playBilling.launch(activity) }
        log(TAG, INFO) { "subscribe(): $outcome" }
        return when (outcome) {
            LaunchOutcome.Launched -> SubscribeResult.Launched
            LaunchOutcome.Canceled -> SubscribeResult.Canceled
            LaunchOutcome.AlreadyOwned -> {
                reconcile("already-owned")
                SubscribeResult.AlreadyOwned
            }

            LaunchOutcome.Unavailable -> SubscribeResult.Unavailable
            is LaunchOutcome.Failed -> SubscribeResult.Failed(null)
        }
    }

    override suspend fun showInAppMessages(activity: Activity) {
        playBilling.showInAppMessages(activity) {
            enqueue("status-updated", requestAll = true)
        }
    }

    /** Requests that arrive while a reconcile runs are merged into one more. */
    private fun enqueue(reason: String, requestAll: Boolean): CompletableDeferred<Unit> = synchronized(this) {
        val merged = pending?.let {
            Pending(it.reasons + reason, it.requestAll || requestAll, it.done)
        } ?: Pending(listOf(reason), requestAll, CompletableDeferred())
        pending = merged
        if (runner?.isActive != true) runner = appScope.launch { drain() }
        merged.done
    }

    private suspend fun drain() {
        while (true) {
            val next = synchronized(this) {
                pending.also { pending = null } ?: run {
                    runner = null
                    null
                }
            } ?: return
            try {
                operationLock.withLock { reconcileLocked(next.reasons.joinToString(","), next.requestAll) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log(TAG, WARN) { "reconcile(${next.reasons}) failed: ${e.asLog()}" }
            } finally {
                next.done.complete(Unit)
            }
        }
    }

    /** Caller holds [operationLock]. */
    private suspend fun reconcileLocked(reason: String, requestAll: Boolean) {
        log(TAG) { "reconcile($reason, requestAll=$requestAll)" }
        val purchases = refreshPlayLocked(reason) ?: return
        val installationId = serverInstallationId() ?: run {
            log(TAG) { "reconcile($reason): no server session yet" }
            return
        }
        val records = submissions.records(installationId).first()
        val access = accessRepo.state.value?.takeIf { it.installationId == installationId }
        val session = foregroundSession

        purchases.forEach { purchase ->
            val playState = purchase.recordState() ?: return@forEach
            val record = records.firstOrNull { it.tokenHash == purchase.tokenHash }
            val key = "$installationId/${purchase.tokenHash}"
            val submittable = playState == SubscriptionTokenRecord.PlayState.PURCHASED
            val why = when {
                record == null -> "new"
                record.playState != playState -> "changed"
                // Asking again would submit a second time after the submission that may be going now
                submittable && record.hasOpenRequest -> "open"
                submittable && requestAll -> "all"
                // Not in a process that never came to the foreground, background work starts those often
                submittable && session > 0 && access != null && access.tier != AccessState.Tier.SUBSCRIBER &&
                    requestedInSession[key] != session -> "not-subscriber"

                else -> null
            } ?: return@forEach
            if (why == "open") {
                submissions.resume(installationId, purchase.tokenHash, "$reason:$why")
                return@forEach
            }
            submissions.request(installationId, purchase, "$reason:$why")
            if (submittable) requestedInSession[key] = session
        }
    }

    /**
     * Caller holds [operationLock]. Updates offer and ownership, returns the Pro purchases or null when
     * Play could not be asked.
     */
    private suspend fun refreshPlayLocked(reason: String): List<PlayPurchase>? {
        val connection = playBilling.connect()
        if (connection != PlayConnection.Ready) {
            log(TAG, WARN) { "refreshPlay($reason): $connection" }
            offer.value = Offer.Unavailable
            return null
        }

        offer.value = when (val query = playBilling.queryOffer()) {
            is OfferQuery.Found -> Offer.Available(
                formattedPrice = query.offer.formattedPrice,
                billingPeriod = query.offer.billingPeriod,
                trialPeriod = query.offer.trialPeriod,
            )

            OfferQuery.Missing, is OfferQuery.Failed -> Offer.Unavailable
        }

        return when (val query = playBilling.queryPurchases()) {
            is PurchaseQuery.Failed -> {
                log(TAG, WARN) { "refreshPlay($reason): purchases unknown, code ${query.code}" }
                null
            }

            is PurchaseQuery.Success -> {
                val pro = query.purchases.filter { PlaySubscriptionProduct.PRODUCT_ID in it.products }
                ownership.value = ownershipOf(pro)
                reportedTokens.value = pro.map { it.tokenHash }.toSet()
                log(TAG) { "refreshPlay($reason): $pro -> ${ownership.value}" }
                pro
            }
        }
    }

    private suspend fun onPurchaseUpdate(update: PurchaseUpdate) {
        log(TAG) { "onPurchaseUpdate($update)" }
        when (update) {
            is PurchaseUpdate.Purchases -> try {
                operationLock.withLock { onPurchasesLocked(update.purchases) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log(TAG, WARN) { "onPurchaseUpdate() failed: ${e.asLog()}" }
            }

            PurchaseUpdate.Canceled -> Unit
            PurchaseUpdate.AlreadyOwned -> reconcile("already-owned")
            is PurchaseUpdate.Failed -> log(TAG, WARN) { "Purchase failed, code ${update.code}" }
        }
    }

    /** Caller holds [operationLock]. */
    private suspend fun onPurchasesLocked(purchases: List<PlayPurchase>) {
        val pro = purchases.filter { PlaySubscriptionProduct.PRODUCT_ID in it.products }
        reportedTokens.update { known -> known.orEmpty() + pro.map { it.tokenHash } }

        val installationId = serverInstallationId()
        if (installationId == null) {
            log(TAG, WARN) { "onPurchases(): no server session, a later reconcile hands them over" }
        } else {
            val records = submissions.records(installationId).first()
            pro.forEach { purchase ->
                val playState = purchase.recordState() ?: return@forEach
                val record = records.firstOrNull { it.tokenHash == purchase.tokenHash }
                // Play reports the same purchase more than once
                if (record != null && record.hasOpenRequest && record.playState == playState) {
                    log(TAG) { "onPurchases(): #${purchase.tokenHash} is already requested" }
                    return@forEach
                }
                submissions.request(installationId, purchase, "purchase-update")
                if (playState == SubscriptionTokenRecord.PlayState.PURCHASED) {
                    requestedInSession["$installationId/${purchase.tokenHash}"] = foregroundSession
                }
            }
        }

        refreshPlayLocked("purchase-update")
    }

    private suspend fun serverInstallationId(): String? {
        if (!privacyAcceptance.isAccepted()) return null
        return (sessionManager.state.value as? SessionState.Active)?.installationId
    }

    private suspend fun refreshAccess(reason: String) {
        try {
            accessRepo.refresh(reason)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(TAG, WARN) { "refreshAccess($reason) failed: ${e.asLog()}" }
        }
    }

    companion object {
        private val TAG = logTag("Upgrade", "Subscription", "Billing")

        internal fun manageUrl(packageName: String): String =
            "https://play.google.com/store/account/subscriptions" +
                "?sku=${PlaySubscriptionProduct.PRODUCT_ID}&package=$packageName"

        /** Null for purchases [SubscriptionSubmissions.request] does not record. */
        private fun PlayPurchase.recordState(): SubscriptionTokenRecord.PlayState? = when {
            isSuspended -> SubscriptionTokenRecord.PlayState.SUSPENDED
            state == PlayPurchase.State.PURCHASED -> SubscriptionTokenRecord.PlayState.PURCHASED
            state == PlayPurchase.State.PENDING -> SubscriptionTokenRecord.PlayState.PENDING
            else -> null
        }

        internal fun ownershipOf(proPurchases: List<PlayPurchase>): Ownership {
            val purchased = proPurchases.filter { it.state == PlayPurchase.State.PURCHASED }
            if (purchased.isEmpty()) return Ownership.None
            return Ownership.Owned(suspended = purchased.all { it.isSuspended })
        }

        /**
         * [reported] limits [records] to the tokens Play still reports, null before Play answered.
         * Updating > PendingInPlay > Rejected > NotAttached > Idle.
         */
        internal fun submissionOf(
            updating: Boolean,
            records: List<SubscriptionTokenRecord>,
            reported: Set<String>?,
        ): Submission {
            val relevant = if (reported == null) records else records.filter { it.tokenHash in reported }
            return when {
                // A suspended token keeps its open request for when Play reports it purchased again
                updating || relevant.any {
                    it.hasOpenRequest && it.playState == SubscriptionTokenRecord.PlayState.PURCHASED
                } -> Submission.Updating
                relevant.any { it.playState == SubscriptionTokenRecord.PlayState.PENDING } -> Submission.PendingInPlay
                relevant.any { it.submission == SubscriptionTokenRecord.Submission.REJECTED } -> Submission.Rejected
                relevant.any { it.submission == SubscriptionTokenRecord.Submission.NOT_ATTACHED } -> Submission.NotAttached
                else -> Submission.Idle
            }
        }
    }
}
