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
    /** Plain text, e.g. an ICAO type like "B738". */
    val text: String? = null,
) {
    /** The first non-null list; an empty list still takes precedence. Without any list, a non-blank [text]. */
    val terms: List<String>?
        get() = hexes ?: squawks ?: callsigns ?: text?.takeIf { it.isNotBlank() }?.let { listOf(it) }
}
