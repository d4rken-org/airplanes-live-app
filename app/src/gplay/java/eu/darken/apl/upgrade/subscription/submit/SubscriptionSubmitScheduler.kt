package eu.darken.apl.upgrade.subscription.submit

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.server.ServerClock
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One queue per token: a request never cancels a submission that is going, it runs after it. The
 * work carries no token, the worker reads it from [SubscriptionTokenStore].
 */
@Singleton
class SubscriptionSubmitScheduler @Inject constructor(
    private val workManager: WorkManager,
    private val serverClock: ServerClock,
) {

    /** Runs no earlier than the record's [SubscriptionTokenRecord.retryNotBefore]. */
    fun schedule(record: SubscriptionTokenRecord, reason: String) {
        enqueue(record.installationId, record.tokenHash, waitFor(record), reason)
    }

    /** Called from the running worker, the follow-up is queued behind it. */
    fun followUp(installationId: String, tokenHash: String, delay: Duration, reason: String) {
        enqueue(installationId, tokenHash, delay, reason)
    }

    fun waitFor(record: SubscriptionTokenRecord): Duration {
        val notBefore = record.retryNotBefore ?: return Duration.ZERO
        return Duration.ofMillis((notBefore - serverClock.now().toEpochMilli()).coerceAtLeast(0L))
    }

    private fun enqueue(installationId: String, tokenHash: String, delay: Duration, reason: String) {
        log(TAG) { "enqueue(#$tokenHash, $reason, in $delay)" }
        val request = OneTimeWorkRequestBuilder<SubscriptionSubmitWorker>()
            .setInitialDelay(delay)
            .setInputData(
                workDataOf(
                    SubscriptionSubmitWorker.KEY_INSTALLATION_ID to installationId,
                    SubscriptionSubmitWorker.KEY_TOKEN_HASH to tokenHash,
                )
            )
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueueUniqueWork(workName(tokenHash), ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    companion object {
        fun workName(tokenHash: String) = "subscription.submit.$tokenHash"
        private val TAG = logTag("Upgrade", "Subscription", "Submit", "Scheduler")
    }
}
