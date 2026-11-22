package eu.darken.apl.upgrade.subscription.submit

import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.upgrade.subscription.play.PlayPurchase
import eu.darken.apl.upgrade.subscription.play.PlaySubscriptionProduct
import eu.darken.apl.upgrade.subscription.submit.SubscriptionTokenRecord.PlayState
import eu.darken.apl.upgrade.subscription.submit.SubscriptionTokenRecord.Submission
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Records purchases and hands them to the server in the background, the request survives the process. */
@Singleton
class SubscriptionSubmissions @Inject constructor(
    private val store: SubscriptionTokenStore,
    private val scheduler: SubscriptionSubmitScheduler,
    private val policyWait: SubscriptionPolicyWait,
) {

    fun records(installationId: String): Flow<List<SubscriptionTokenRecord>> = store.tokens
        .map { it.recordsOf(installationId) }
        .distinctUntilChanged()

    /** True while a submission was answered but the access policy may not reflect it yet. */
    val updating: Flow<Boolean>
        get() = policyWait.updating

    /**
     * Stores [purchase] for [installationId] and, for a purchased one that is not suspended, asks
     * for a submission. Each call asks again, calls that arrive before a submission starts share it.
     * Returns null when the purchase is not one to record.
     */
    suspend fun request(installationId: String, purchase: PlayPurchase, reason: String): SubscriptionTokenRecord? {
        if (PlaySubscriptionProduct.PRODUCT_ID !in purchase.products) {
            log(TAG, WARN) { "request($reason): ignoring #${purchase.tokenHash} for ${purchase.products}" }
            return null
        }
        val playState = when {
            purchase.isSuspended -> PlayState.SUSPENDED
            purchase.state == PlayPurchase.State.PURCHASED -> PlayState.PURCHASED
            purchase.state == PlayPurchase.State.PENDING -> PlayState.PENDING
            else -> {
                log(TAG, WARN) { "request($reason): ignoring #${purchase.tokenHash} in state ${purchase.state}" }
                return null
            }
        }
        val submit = playState == PlayState.PURCHASED

        val tokenHash = purchase.tokenHash
        val stored = store.update { tokens ->
            val record = tokens.find(installationId, tokenHash) ?: SubscriptionTokenRecord(
                installationId = installationId,
                tokenHash = tokenHash,
                token = purchase.token,
                productId = PlaySubscriptionProduct.PRODUCT_ID,
            )
            val updated = record.copy(playState = playState).let {
                if (submit) it.copy(requestedGen = it.requestedGen + 1, submission = Submission.REQUESTED) else it
            }
            tokens.upsert(updated)
        }.find(installationId, tokenHash)!!

        log(TAG) { "request($reason): $stored" }
        if (submit) scheduler.schedule(stored, reason)
        return stored
    }

    companion object {
        private val TAG = logTag("Upgrade", "Subscription", "Submissions")
    }
}
