package eu.darken.apl.search.core

import eu.darken.apl.search.ui.SearchViewModel

/** One server term searches hex, callsign, registration, aircraft type and squawk at once. */
data class SearchQuery(
    val terms: List<SearchTerm> = emptyList(),
) {
    val isEmpty: Boolean
        get() = terms.isEmpty()
}

data class SearchTerm(
    val text: String = "",
    val categories: Set<SearchCategory> = emptySet(),
) {
    val isEmpty: Boolean
        get() = text.isBlank() && categories.isEmpty()

    /** Identifies the term when correlating a batch outcome back to its input. */
    val id: String
        get() = if (text.isNotBlank()) text else categories.joinToString("+") { it.wire }
}

enum class SearchCategory(val wire: String) {
    MILITARY("military"),
    LADD("ladd"),
    PIA("pia"),
    ;

    companion object {
        fun fromWire(raw: String): SearchCategory? = entries.firstOrNull { it.wire == raw.trim().lowercase() }
    }
}

/**
 * Position searches are not terms, they are answered by a nearby snapshot, so this returns an empty
 * query for that mode.
 */
fun buildSearchQuery(mode: SearchViewModel.State.Mode, raw: String): SearchQuery {
    val tokens = raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
    return when (mode) {
        SearchViewModel.State.Mode.INTERESTING -> {
            val categories = tokens.mapNotNull { SearchCategory.fromWire(it) }.toSet()
            SearchQuery(listOf(SearchTerm(categories = categories.ifEmpty { SearchCategory.entries.toSet() })))
        }

        SearchViewModel.State.Mode.POSITION -> SearchQuery()

        else -> SearchQuery(tokens.map { SearchTerm(text = it) })
    }
}
