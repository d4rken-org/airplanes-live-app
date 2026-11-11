package eu.darken.apl.airport.ui

import eu.darken.apl.airport.core.Airport
import eu.darken.apl.airport.core.AirportTarget
import eu.darken.apl.common.displayCountry
import java.util.Locale

/** What the details sheet shows, null where it is unknown. */
data class AirportDetailsDisplay(
    val title: String,
    val codes: List<Code>,
    val municipality: String? = null,
    val region: String? = null,
    val country: String? = null,
    val type: Type? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
) {
    data class Code(val kind: CodeKind, val value: String)

    enum class CodeKind { IATA, ICAO, IDENT, LOCAL }

    /** [raw] is the server's value, shown when this build does not know the [size]. */
    data class Type(val size: Airport.Size, val raw: String)

    companion object {
        /**
         * The server's airport, [resolved] or the one [target] carries, wins where it has a value, a partial
         * target fills in what it left out.
         */
        fun from(target: AirportTarget, resolved: Airport? = null): AirportDetailsDisplay {
            val server = resolved ?: (target as? AirportTarget.Full)?.airport
            val ref = (target as? AirportTarget.Partial)?.ref
            // Frankfurt: icao EDDF, ident EDDF -> rows IATA FRA, ICAO EDDF
            val codes = listOf(
                CodeKind.IATA to (server?.iata ?: ref?.iata),
                CodeKind.ICAO to (server?.icao ?: ref?.icao),
                CodeKind.IDENT to server?.ident,
                CodeKind.LOCAL to server?.local,
            )
                .mapNotNull { (kind, code) -> code?.let { Code(kind, it) } }
                .distinctBy { it.value.uppercase(Locale.ROOT) }

            return AirportDetailsDisplay(
                title = server?.name ?: target.name,
                codes = codes,
                municipality = server?.municipality,
                region = server?.region,
                country = displayCountry(server?.country) ?: ref?.countryName,
                type = server?.let { Type(it.size, it.type) },
                latitude = server?.latitude ?: ref?.latitude,
                longitude = server?.longitude ?: ref?.longitude,
            )
        }
    }
}
