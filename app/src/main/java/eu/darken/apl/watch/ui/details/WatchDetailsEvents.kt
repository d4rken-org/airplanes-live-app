package eu.darken.apl.watch.ui.details

sealed class WatchDetailsEvents {
    data class RemovalConfirmation(val id: String) : WatchDetailsEvents()

    /** The server has the aircraft's history, the user's tier does not include it. */
    data object HistoryUpsell : WatchDetailsEvents()
}
