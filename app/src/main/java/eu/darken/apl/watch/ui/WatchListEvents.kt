package eu.darken.apl.watch.ui

sealed interface WatchListEvents {
    data object RequestNotificationPermission : WatchListEvents
}
