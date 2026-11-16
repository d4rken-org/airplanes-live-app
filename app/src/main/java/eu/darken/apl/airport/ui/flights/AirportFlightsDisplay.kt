package eu.darken.apl.airport.ui.flights

import eu.darken.apl.airport.core.AirportFlightList
import eu.darken.apl.common.flagEmoji
import eu.darken.apl.server.api.AirportFlightEntryDto
import eu.darken.apl.server.api.AirportFlightTimeDto
import eu.darken.apl.server.api.AirportFlightsSourceDto
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

/**
 * What the flights section shows of a list, worked out for one moment. Values the section turns into text stay
 * typed, wire values this build does not know are already dropped or mapped to their fallback.
 */
data class AirportFlightsDisplay(
    val sources: List<Source>,
    /** Arrivals, departures and other, in this order. */
    val sections: List<Section>,
) {
    val isEmpty: Boolean get() = sections.all { it.rows.isEmpty() }

    /** Arrivals and departures always, other only while it has something to show. */
    val visibleSegments: List<SectionKind>
        get() = SectionKind.entries.filter { kind ->
            kind != SectionKind.OTHER || sections.any { it.kind == kind && it.hasContent }
        }

    /**
     * [previous] while it is still visible, even when its section emptied. Otherwise arrivals if they have
     * something to show, else the first section that has, else arrivals.
     */
    fun selection(previous: SectionKind?): SectionKind {
        val visible = visibleSegments
        if (previous != null && previous in visible) return previous
        return SectionKind.entries.firstOrNull { kind -> sections.any { it.kind == kind && it.hasContent } }
            ?: SectionKind.ARRIVALS
    }

    data class Source(val kind: SourceKind, val state: SourceState)

    enum class SourceKind(val wire: String?) {
        KNOWN_ROUTES(AirportFlightsSourceDto.KIND_KNOWN_ROUTES),
        CACHED_ROUTES(AirportFlightsSourceDto.KIND_CACHED_ROUTES),
        NEARBY_TRAFFIC(AirportFlightsSourceDto.KIND_NEARBY_TRAFFIC),
        HISTORY(AirportFlightsSourceDto.KIND_HISTORY),
        SCHEDULES(AirportFlightsSourceDto.KIND_SCHEDULES),
        OTHER(null),
    }

    /** From good to bad, the order the worst of several states is picked by. */
    enum class SourceState(val wire: String?) {
        OK(AirportFlightsSourceDto.STATE_OK),
        PARTIAL(AirportFlightsSourceDto.STATE_PARTIAL),
        STALE(AirportFlightsSourceDto.STATE_STALE),
        UNAVAILABLE(AirportFlightsSourceDto.STATE_UNAVAILABLE),
    }

    enum class SectionKind { ARRIVALS, DEPARTURES, OTHER }

    /**
     * [count] is what the server counted for the section's directions. [showMoreDirection] is the wire direction
     * whose next page "Show more" loads, null when none has one.
     */
    data class Section(
        val kind: SectionKind,
        val count: Int,
        val rows: List<Row>,
        val showMoreDirection: String?,
    ) {
        val hasContent: Boolean get() = rows.isNotEmpty() || showMoreDirection != null
    }

    /**
     * [id] is unique within the display. [time] is null when the entry carries no time, which marks a flight
     * happening now. [aircraftHex] is the aircraft the row opens.
     */
    data class Row(
        val id: String,
        val direction: String,
        val identity: Identity,
        val otherAirport: OtherAirport?,
        val status: FlightStatus?,
        val estimated: Boolean,
        val time: EventTime?,
        val evidence: List<Evidence>,
        val aircraftHex: String?,
    )

    sealed interface Identity {
        /** At least one of both is set. */
        data class Flight(val callsign: String?, val number: String?) : Identity
        data class Registration(val registration: String) : Identity
        data class Hex(val hex: String) : Identity
        data object Unidentified : Identity
    }

    /**
     * [code] is the IATA, ICAO or other identifier, in this preference. At least one of [code] and [name] is set.
     * [flag] is the emoji of the airport's country.
     */
    data class OtherAirport(val code: String?, val name: String?, val flag: String?)

    enum class FlightStatus(val wire: String) {
        ON_GROUND(AirportFlightEntryDto.STATUS_ON_GROUND),
        APPROACHING(AirportFlightEntryDto.STATUS_APPROACHING),
        DEPARTED(AirportFlightEntryDto.STATUS_DEPARTED),
        EN_ROUTE(AirportFlightEntryDto.STATUS_EN_ROUTE),
        SCHEDULED(AirportFlightEntryDto.STATUS_SCHEDULED),
        DELAYED(AirportFlightEntryDto.STATUS_DELAYED),
        BOARDING(AirportFlightEntryDto.STATUS_BOARDING),
        LANDED(AirportFlightEntryDto.STATUS_LANDED),
        CANCELLED(AirportFlightEntryDto.STATUS_CANCELLED),
        DIVERTED(AirportFlightEntryDto.STATUS_DIVERTED),
    }

    enum class Evidence(val wire: String) {
        KNOWN_ROUTE(AirportFlightEntryDto.EVIDENCE_KNOWN_ROUTE),
        CACHED_ROUTE(AirportFlightEntryDto.EVIDENCE_CACHED_ROUTE),
        FITTED_LEG(AirportFlightEntryDto.EVIDENCE_FITTED_LEG),
        NEARBY_LOW(AirportFlightEntryDto.EVIDENCE_NEARBY_LOW),
        ON_GROUND(AirportFlightEntryDto.EVIDENCE_ON_GROUND),
        DESCENDING(AirportFlightEntryDto.EVIDENCE_DESCENDING),
        CLIMBING(AirportFlightEntryDto.EVIDENCE_CLIMBING),
        HISTORY(AirportFlightEntryDto.EVIDENCE_HISTORY),
        SCHEDULE(AirportFlightEntryDto.EVIDENCE_SCHEDULE),
    }

    enum class TimeKind { SCHEDULED, EXPECTED, ACTUAL }

    enum class Basis(val wire: String) {
        GATE(AirportFlightTimeDto.BASIS_GATE),
        RUNWAY(AirportFlightTimeDto.BASIS_RUNWAY),
    }

    data class EventTime(val kind: TimeKind, val basis: Basis?, val clock: ClockTime)

    companion object {
        /** [airportTimeZone] is the airport's zone from elsewhere, for a list that names none. */
        fun from(
            list: AirportFlightList,
            airportTimeZone: String?,
            now: Instant,
            deviceZone: ZoneId,
        ): AirportFlightsDisplay {
            val zone = DisplayZone.resolve(listOf(list.airport?.timeZone, airportTimeZone), deviceZone)

            val arrivals = listOfNotNull(list.direction(AirportFlightEntryDto.DIRECTION_ARRIVAL))
            val departures = listOfNotNull(list.direction(AirportFlightEntryDto.DIRECTION_DEPARTURE))
            val others = list.directions.filter {
                it.direction != AirportFlightEntryDto.DIRECTION_ARRIVAL &&
                    it.direction != AirportFlightEntryDto.DIRECTION_DEPARTURE
            }

            fun section(kind: SectionKind, directions: List<AirportFlightList.Direction>) = Section(
                kind = kind,
                count = directions.sumOf { maxOf(it.total ?: 0, it.entries.size) },
                rows = directions.flatMap { direction ->
                    direction.entries.map { it.toRow(list, zone, now) }
                },
                showMoreDirection = directions.firstOrNull { it.next != null }?.direction,
            )

            return AirportFlightsDisplay(
                sources = list.sources.toDisplaySources(),
                sections = listOf(
                    section(SectionKind.ARRIVALS, arrivals),
                    section(SectionKind.DEPARTURES, departures),
                    section(SectionKind.OTHER, others),
                ),
            )
        }

        /** Known kinds in server order, every other kind folded into one [SourceKind.OTHER] at the end. */
        private fun List<AirportFlightsSourceDto>.toDisplaySources(): List<Source> {
            val mapped = map { dto ->
                Source(
                    kind = SourceKind.entries.firstOrNull { it.wire == dto.kind } ?: SourceKind.OTHER,
                    state = SourceState.entries.firstOrNull { it.wire == dto.state } ?: SourceState.STALE,
                )
            }
            val other = mapped.filter { it.kind == SourceKind.OTHER }.maxByOrNull { it.state }
            return mapped.filter { it.kind != SourceKind.OTHER } + listOfNotNull(other)
        }

        private fun AirportFlightEntryDto.toRow(list: AirportFlightList, zone: DisplayZone, now: Instant): Row {
            val aircraft = aircraftId?.let { list.aircraft[it] }
            val callsign = flight?.callsign?.trim()?.takeIf { it.isNotEmpty() }
            val number = flight?.number?.trim()?.takeIf { it.isNotEmpty() }
            val registration = aircraft?.registration?.trim()?.takeIf { it.isNotEmpty() }
            val identity = when {
                callsign != null || number != null -> Identity.Flight(callsign, number)
                registration != null -> Identity.Registration(registration)
                aircraft != null -> Identity.Hex(aircraft.id.uppercase(Locale.ROOT))
                else -> Identity.Unidentified
            }
            val other = otherAirport?.let { airport ->
                val code = listOf(airport.iata, airport.icao, airport.ident).firstOrNull { !it.isNullOrBlank() }
                val name = airport.name?.takeIf { it.isNotBlank() }
                if (code == null && name == null) null else OtherAirport(code, name, flagEmoji(airport.country))
            }
            val time = times?.let { times ->
                listOfNotNull(
                    times.actual?.let { TimeKind.ACTUAL to it },
                    times.estimated?.let { TimeKind.EXPECTED to it },
                    times.scheduled?.let { TimeKind.SCHEDULED to it },
                ).firstOrNull()
            }?.let { (kind, dto) ->
                EventTime(
                    kind = kind,
                    basis = Basis.entries.firstOrNull { it.wire == dto.basis },
                    clock = zone.clockTime(Instant.ofEpochMilli(dto.at), now),
                )
            }

            return Row(
                id = "$direction/$key",
                direction = direction,
                identity = identity,
                otherAirport = other,
                status = FlightStatus.entries.firstOrNull { it.wire == status },
                estimated = estimated == true,
                time = time,
                evidence = evidence.mapNotNull { wire -> Evidence.entries.firstOrNull { it.wire == wire } },
                aircraftHex = aircraft?.id,
            )
        }
    }
}

/**
 * A moment as the clock in a [DisplayZone] shows it. [offset] is the zone's offset at that moment, [date] is set
 * when it falls on another day than the moment the display was made for.
 */
data class ClockTime(
    val local: LocalDateTime,
    val offset: ZoneOffset,
    val date: LocalDate?,
)

/** The zone the section's times are shown in. */
data class DisplayZone(val zone: ZoneId) {

    fun clockTime(at: Instant, now: Instant): ClockTime {
        val zoned = at.atZone(zone)
        val today = now.atZone(zone).toLocalDate()
        return ClockTime(
            local = zoned.toLocalDateTime(),
            offset = zoned.offset,
            date = zoned.toLocalDate().takeIf { it != today },
        )
    }

    companion object {
        /** The first of [airportZones] that names a zone ("Europe/Berlin" does, "Mars/Olympus" not), else [deviceZone]. */
        fun resolve(airportZones: List<String?>, deviceZone: ZoneId): DisplayZone = airportZones
            .firstNotNullOfOrNull { id -> id?.let { runCatching { ZoneId.of(it) }.getOrNull() } }
            ?.let { DisplayZone(it) }
            ?: DisplayZone(deviceZone)
    }
}
