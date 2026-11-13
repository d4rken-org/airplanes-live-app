package eu.darken.apl.map.ui

import eu.darken.apl.airport.core.AirportRef
import eu.darken.apl.airport.core.AirportTarget
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class MapTapTargetTest : BaseTest() {

    private val muc = airport(icao = "EDDM", iata = "MUC")
    private val fra = airport(icao = "EDDF", iata = "FRA")
    private val egelsbach = airport(icao = "EDFE", iata = null)

    private fun airport(icao: String, iata: String?) = AirportTarget.Partial(AirportRef.create(icao = icao, iata = iata)!!)

    private fun <T> T.at(distance: Float) = TapCandidate(this, distance)

    private fun tap(
        aircraftAtPoint: List<TapCandidate<String>> = emptyList(),
        airportLabelsAtPoint: List<TapCandidate<AirportTarget>> = emptyList(),
        aircraftNearby: List<TapCandidate<String>> = emptyList(),
        selectedAirportNearby: AirportTarget? = null,
        airportsNearby: List<TapCandidate<AirportTarget>> = emptyList(),
    ) = mapTapTarget(aircraftAtPoint, airportLabelsAtPoint, aircraftNearby, selectedAirportNearby, airportsNearby)

    @Test
    fun `an aircraft under the finger beats a label under the finger`() {
        tap(
            aircraftAtPoint = listOf("abc123".at(10f)),
            airportLabelsAtPoint = listOf(muc.at(2f)),
            aircraftNearby = listOf("abc123".at(10f)),
            selectedAirportNearby = fra,
            airportsNearby = listOf(muc.at(2f)),
        ) shouldBe MapTapTarget.Aircraft("abc123")
    }

    @Test
    fun `a label under the finger beats an aircraft near it`() {
        tap(
            airportLabelsAtPoint = listOf(muc.at(12f)),
            aircraftNearby = listOf("abc123".at(3f)),
            selectedAirportNearby = fra,
            airportsNearby = listOf(muc.at(12f)),
        ) shouldBe MapTapTarget.Airport(muc)
    }

    @Test
    fun `the selected airport's label under the finger opens the selected airport`() {
        tap(
            airportLabelsAtPoint = listOf(fra.at(4f)),
            aircraftNearby = listOf("abc123".at(3f)),
            selectedAirportNearby = fra,
        ) shouldBe MapTapTarget.Airport(fra)
    }

    @Test
    fun `an aircraft near the finger beats an airport near it`() {
        tap(
            aircraftNearby = listOf("abc123".at(14f)),
            selectedAirportNearby = fra,
            airportsNearby = listOf(muc.at(1f)),
        ) shouldBe MapTapTarget.Aircraft("abc123")
    }

    @Test
    fun `the selected airport near the finger beats other airports near it`() {
        tap(
            selectedAirportNearby = fra,
            airportsNearby = listOf(muc.at(1f)),
        ) shouldBe MapTapTarget.Airport(fra)
    }

    @Test
    fun `an airport near the finger beats a map tap`() {
        tap(airportsNearby = listOf(muc.at(15f))) shouldBe MapTapTarget.Airport(muc)
    }

    @Test
    fun `nothing near the finger is a map tap`() {
        tap() shouldBe MapTapTarget.Map
    }

    @Test
    fun `the nearest wins within a level`() {
        tap(aircraftAtPoint = listOf("far".at(9f), "near".at(2f))) shouldBe MapTapTarget.Aircraft("near")
        tap(airportLabelsAtPoint = listOf(muc.at(9f), egelsbach.at(2f))) shouldBe MapTapTarget.Airport(egelsbach)
        tap(aircraftNearby = listOf("far".at(9f), "near".at(2f))) shouldBe MapTapTarget.Aircraft("near")
        tap(airportsNearby = listOf(muc.at(9f), egelsbach.at(2f))) shouldBe MapTapTarget.Airport(egelsbach)
    }

    @Test
    fun `without labels, as below the close zoom, the nearest aircraft wins over any airport`() {
        // The enlarged selected aircraft is under the finger, a neighbour's anchor is nearer
        tap(
            aircraftAtPoint = listOf("selected".at(20f)),
            aircraftNearby = listOf("neighbour".at(5f)),
            selectedAirportNearby = fra,
            airportsNearby = listOf(muc.at(1f)),
        ) shouldBe MapTapTarget.Aircraft("neighbour")
        tap(
            aircraftNearby = listOf("abc123".at(15f)),
            airportsNearby = listOf(muc.at(1f)),
        ) shouldBe MapTapTarget.Aircraft("abc123")
        tap(airportsNearby = listOf(muc.at(1f))) shouldBe MapTapTarget.Airport(muc)
    }
}
