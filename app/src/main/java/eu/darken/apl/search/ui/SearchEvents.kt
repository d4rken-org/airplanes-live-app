package eu.darken.apl.search.ui

sealed interface SearchEvents {
    data object RequestLocationPermission : SearchEvents
    data object LocationUnavailable : SearchEvents
    data object PlaceSearchUnavailable : SearchEvents
    data class PlaceNotFound(val place: String) : SearchEvents
}
