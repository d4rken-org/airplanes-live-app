package eu.darken.apl.search.ui

sealed interface SearchEvents {
    data object RequestLocationPermission : SearchEvents
    data class SearchError(val error: Throwable) : SearchEvents
}