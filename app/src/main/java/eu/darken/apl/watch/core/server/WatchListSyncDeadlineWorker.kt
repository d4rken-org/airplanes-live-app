package eu.darken.apl.watch.core.server

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag

/** Counterpart: [WatchListSyncScheduler.deadline]. */
@HiltWorker
class WatchListSyncDeadlineWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val scheduler: WatchListSyncScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val reason = inputData.getString(WatchListSyncWorker.KEY_REASON) ?: "deadline"
        log(TAG) { "Deadline reached: $reason" }
        scheduler.requestSync(reason)
        return Result.success()
    }

    companion object {
        private val TAG = logTag("Watch", "List", "Deadline")
    }
}
