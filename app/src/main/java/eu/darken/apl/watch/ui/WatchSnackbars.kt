package eu.darken.apl.watch.ui

import android.content.Context
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.ui.platform.AccessibilityManager
import eu.darken.apl.R
import eu.darken.apl.watch.core.WatchLimitReachedException
import kotlinx.coroutines.withTimeoutOrNull

/** True when Undo was tapped. */
suspend fun SnackbarHostState.showWatchAdded(
    context: Context,
    label: String,
    accessibilityManager: AccessibilityManager?,
): Boolean {
    // Shorter than SnackbarDuration.Short, an Undo only matters right after the tap
    val timeout = accessibilityManager?.calculateRecommendedTimeoutMillis(
        UNDO_TIMEOUT_MS,
        containsIcons = false,
        containsText = true,
        containsControls = true,
    ) ?: UNDO_TIMEOUT_MS
    val result = withTimeoutOrNull(timeout) {
        showSnackbar(
            message = context.getString(R.string.watch_item_x_added, label),
            actionLabel = context.getString(R.string.common_undo_action),
            duration = SnackbarDuration.Indefinite,
        )
    }
    return result == SnackbarResult.ActionPerformed
}

/** True when Upgrade was tapped. */
suspend fun SnackbarHostState.showWatchLimitReached(context: Context, error: WatchLimitReachedException): Boolean {
    val result = showSnackbar(
        message = error.getLocalizedError(context).description.get(context),
        actionLabel = context.getString(R.string.common_upgrade_action),
        duration = SnackbarDuration.Long,
    )
    return result == SnackbarResult.ActionPerformed
}

/** True when Upgrade was tapped. */
suspend fun SnackbarHostState.showHistoryNoAccess(context: Context): Boolean {
    val result = showSnackbar(
        message = context.getString(R.string.map_history_no_access),
        actionLabel = context.getString(R.string.common_upgrade_action),
        duration = SnackbarDuration.Long,
    )
    return result == SnackbarResult.ActionPerformed
}

private const val UNDO_TIMEOUT_MS = 3_000L
