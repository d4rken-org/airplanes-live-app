package eu.darken.apl.search.core

import eu.darken.apl.main.core.aircraft.Aircraft
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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

    /** Every one of them has to match, each in any field. */
    val words: List<String>
        get() = text.split(WHITESPACE).filter { it.isNotBlank() }

    /** Identifies the term when correlating a batch outcome back to its input. */
    val id: String
        get() = if (text.isNotBlank()) text else categories.joinToString("+") { it.wire }
}

@Serializable
enum class SearchCategory(val wire: String) {
    @SerialName("military") MILITARY("military"),
    @SerialName("ladd") LADD("ladd"),
    @SerialName("pia") PIA("pia"),
    ;

    fun matches(aircraft: Aircraft): Boolean = when (this) {
        MILITARY -> aircraft.military
        LADD -> aircraft.ladd
        PIA -> aircraft.pia
    }
}

/** What the search screen holds, persisted as is. */
@Serializable
data class SearchInput(
    @SerialName("text") val text: String = "",
    @SerialName("categories") val categories: Set<SearchCategory> = emptySet(),
    @SerialName("nearby") val nearby: Boolean = false,
    /** Where nearby looks, null is the device location. */
    @SerialName("place") val place: String? = null,
) {
    val isEmpty: Boolean
        get() = !nearby && buildSearchQuery(this).isEmpty
}

/**
 * Commas separate terms, words within a term must all match: `DLH  a320, 3C65A3` becomes `DLH a320`
 * and `3C65A3`. Selected categories narrow each term, and stand alone as one term when there is no
 * text.
 */
fun buildSearchQuery(input: SearchInput): SearchQuery {
    val texts = input.text
        .split(",")
        .map { it.split(WHITESPACE).filter { word -> word.isNotBlank() }.joinToString(" ") }
        .filter { it.isNotEmpty() }
        .distinctBy { it.uppercase() }

    return when {
        texts.isNotEmpty() -> SearchQuery(texts.map { SearchTerm(text = it, categories = input.categories) })
        input.categories.isNotEmpty() -> SearchQuery(listOf(SearchTerm(categories = input.categories)))
        else -> SearchQuery()
    }
}

// Pasted text brings no-break spaces along, \s alone misses them
private val WHITESPACE = Regex("[\\p{Z}\\s]+")
