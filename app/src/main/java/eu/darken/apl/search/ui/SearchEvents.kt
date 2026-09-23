package eu.darken.apl.search.ui

import eu.darken.apl.search.core.SearchRepo

sealed interface SearchEvents {
    data object RequestLocationPermission : SearchEvents
    data object LocationUnavailable : SearchEvents
    data object PlaceSearchUnavailable : SearchEvents
    data class PlaceNotFound(val place: String) : SearchEvents
    data class SearchError(
        val error: Throwable,
        val charged: SearchRepo.Charged = SearchRepo.Charged.SEARCH,
    ) : SearchEvents
}
