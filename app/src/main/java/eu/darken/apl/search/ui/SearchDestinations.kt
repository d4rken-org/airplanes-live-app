package eu.darken.apl.search.ui

import eu.darken.apl.common.navigation.SingleInstanceDestination
import kotlinx.serialization.Serializable

@Serializable
data class DestinationSearch(
    val targetHexes: List<String>? = null,
    val targetSquawks: List<String>? = null,
    val targetCallsigns: List<String>? = null,
    /** Makes each targeted open a new entry, so opening the same aircraft again searches again. */
    val requestId: String? = null,
) : SingleInstanceDestination
