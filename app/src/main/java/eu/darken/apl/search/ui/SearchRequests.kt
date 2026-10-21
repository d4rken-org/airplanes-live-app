package eu.darken.apl.search.ui

import eu.darken.apl.common.navigation.TabRequest
import javax.inject.Inject
import javax.inject.Singleton

/** What other screens asked Search to look for. */
@Singleton
class SearchRequests @Inject constructor() : TabRequest<SearchTarget>()

data class SearchTarget(
    val hexes: List<String>? = null,
    val squawks: List<String>? = null,
    val callsigns: List<String>? = null,
) {
    /** The first non-null list; an empty list still takes precedence. */
    val terms: List<String>?
        get() = hexes ?: squawks ?: callsigns
}
