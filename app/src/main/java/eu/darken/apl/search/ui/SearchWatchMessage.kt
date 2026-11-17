package eu.darken.apl.search.ui

import android.content.Context
import androidx.compose.material3.SnackbarDuration
import eu.darken.apl.R

internal data class SearchWatchMessage(
    val text: String,
    val action: Action,
    val duration: SnackbarDuration,
) {
    enum class Action {
        UNDO,
        UPGRADE,
        NONE,
        ;
    }
}

internal fun searchWatchMessage(context: Context, event: SearchEvents.WatchOutcome): SearchWatchMessage {
    val res = context.resources
    return when (event) {
        is SearchEvents.WatchesAdded -> {
            val created = event.createdIds.size
            val existing = event.existingCount
            when {
                created == 0 -> SearchWatchMessage(
                    text = res.getQuantityString(R.plurals.search_watch_all_existing_x, existing, existing),
                    action = SearchWatchMessage.Action.NONE,
                    duration = SnackbarDuration.Short,
                )

                existing == 0 -> SearchWatchMessage(
                    text = res.getQuantityString(R.plurals.search_watch_added_x, created, created),
                    action = SearchWatchMessage.Action.UNDO,
                    duration = SnackbarDuration.Long,
                )

                else -> SearchWatchMessage(
                    text = res.getQuantityString(R.plurals.search_watch_added_existing_x, existing, created, existing),
                    action = SearchWatchMessage.Action.UNDO,
                    duration = SnackbarDuration.Long,
                )
            }
        }

        is SearchEvents.WatchLimitReached -> {
            val limit = event.error.getLocalizedError(context).description.get(context)
            val added = event.addedCount
            SearchWatchMessage(
                text = if (added > 0) {
                    res.getQuantityString(R.plurals.search_watch_added_limit_x, added, added, limit)
                } else {
                    limit
                },
                action = SearchWatchMessage.Action.UPGRADE,
                duration = SnackbarDuration.Long,
            )
        }
    }
}
