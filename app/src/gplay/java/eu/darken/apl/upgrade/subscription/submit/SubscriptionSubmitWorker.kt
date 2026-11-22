package eu.darken.apl.upgrade.subscription.submit

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.Logging.Priority.ERROR
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

@HiltWorker
class SubscriptionSubmitWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val dispatcherProvider: DispatcherProvider,
    private val submitter: SubscriptionSubmitter,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val installationId = inputData.getString(KEY_INSTALLATION_ID)
        val tokenHash = inputData.getString(KEY_TOKEN_HASH)
        if (installationId == null || tokenHash == null) {
            log(TAG, ERROR) { "Missing input, installation=$installationId, token=#$tokenHash" }
            return Result.failure()
        }
        val outcome = try {
            withContext(dispatcherProvider.IO) {
                withTimeout(TIMEOUT_MS) { submitter.submit(installationId, tokenHash) }
            }
        } catch (e: TimeoutCancellationException) {
            log(TAG, WARN) { "#$tokenHash ran into the timeout" }
            SubscriptionSubmitter.Outcome.RETRY
        }
        log(TAG) { "#$tokenHash finished: $outcome" }
        return when (outcome) {
            SubscriptionSubmitter.Outcome.DONE -> Result.success()
            SubscriptionSubmitter.Outcome.RETRY -> Result.retry()
            SubscriptionSubmitter.Outcome.FAILED -> Result.failure()
        }
    }

    companion object {
        const val KEY_INSTALLATION_ID = "installationId"
        const val KEY_TOKEN_HASH = "tokenHash"
        private const val TIMEOUT_MS = 60 * 1000L
        private val TAG = logTag("Upgrade", "Subscription", "Submit", "Worker")
    }
}
