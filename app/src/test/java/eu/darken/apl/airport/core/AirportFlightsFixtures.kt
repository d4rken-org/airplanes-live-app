package eu.darken.apl.airport.core

import eu.darken.apl.server.ServerModule
import eu.darken.apl.server.api.AirportFlightsResponse
import testhelper.json.readTestResource

/** [name] is a file in `src/test/resources/airport-flights`, e.g. `answered-load.json`. */
internal fun airportFlightsFixture(name: String): AirportFlightsResponse = ServerModule.serverJson()
    .decodeFromString(AirportFlightsResponse.serializer(), readTestResource("airport-flights/$name"))

internal fun airportFlightListFixture(name: String): AirportFlightList =
    checkNotNull(AirportFlightList.from(airportFlightsFixture(name))) { "$name is no answered list" }
