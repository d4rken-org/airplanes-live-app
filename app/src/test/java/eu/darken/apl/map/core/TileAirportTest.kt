package eu.darken.apl.map.core

import eu.darken.apl.airport.core.AirportRef
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class TileAirportTest : BaseTest() {

    private fun ref(
        icao: String? = null,
        iata: String? = null,
        nameLatin: String? = null,
        nameEn: String? = null,
        name: String? = null,
        latitude: Double = 50.0333,
        longitude: Double = 8.5706,
    ) = tileAirportRef(
        icao = icao,
        iata = iata,
        nameLatin = nameLatin,
        nameEn = nameEn,
        name = name,
        latitude = latitude,
        longitude = longitude,
    )

    @Test
    fun `an airport with only an ICAO code`() {
        ref(icao = "EDFE", name = "Flugplatz Egelsbach") shouldBe AirportRef.create(
            icao = "EDFE",
            iata = null,
            name = "Flugplatz Egelsbach",
            latitude = 50.0333,
            longitude = 8.5706,
        )
    }

    @Test
    fun `an airport with only an IATA code`() {
        ref(iata = "HHN", name = "Hahn") shouldBe AirportRef.create(
            icao = null,
            iata = "HHN",
            name = "Hahn",
            latitude = 50.0333,
            longitude = 8.5706,
        )
    }

    @Test
    fun `an airport with both codes`() {
        ref(icao = "EDDF", iata = "FRA", name = "Frankfurt am Main") shouldBe AirportRef.create(
            icao = "EDDF",
            iata = "FRA",
            name = "Frankfurt am Main",
            latitude = 50.0333,
            longitude = 8.5706,
        )
    }

    @Test
    fun `blank codes count as missing`() {
        val ref = ref(icao = "EDDF", iata = " ").shouldNotBeNull()
        ref.icao shouldBe "EDDF"
        ref.iata.shouldBeNull()

        val other = ref(icao = "", iata = "FRA").shouldNotBeNull()
        other.icao.shouldBeNull()
        other.iata shouldBe "FRA"
    }

    @Test
    fun `an airport without any code is none`() {
        ref(icao = null, iata = null, name = "Somewhere").shouldBeNull()
        ref(icao = "", iata = "  ", name = "Somewhere").shouldBeNull()
    }

    @Test
    fun `the latin name comes first`() {
        ref(icao = "UUEE", nameLatin = "Sheremetyevo", nameEn = "Sheremetyevo Airport", name = "Шереметьево")
            .shouldNotBeNull().name shouldBe "Sheremetyevo"
    }

    @Test
    fun `without a latin name the english one is used`() {
        ref(icao = "UUEE", nameEn = "Sheremetyevo Airport", name = "Шереметьево")
            .shouldNotBeNull().name shouldBe "Sheremetyevo Airport"
    }

    @Test
    fun `without a latin or english name the local one is used`() {
        ref(icao = "UUEE", name = "Шереметьево").shouldNotBeNull().name shouldBe "Шереметьево"
    }

    @Test
    fun `blank names are skipped`() {
        ref(icao = "UUEE", nameLatin = " ", nameEn = "", name = "Шереметьево").shouldNotBeNull().name shouldBe "Шереметьево"
        ref(icao = "UUEE", nameLatin = "  ", nameEn = "Sheremetyevo Airport", name = "Шереметьево")
            .shouldNotBeNull().name shouldBe "Sheremetyevo Airport"
    }

    @Test
    fun `without any name there is none`() {
        ref(icao = "UUEE", nameLatin = " ", nameEn = null, name = "").shouldNotBeNull().name.shouldBeNull()
    }

    @Test
    fun `a longitude in a world copy is wrapped and kept`() {
        val ref = ref(icao = "EDDF", iata = "FRA", latitude = 50.0333, longitude = 368.5706).shouldNotBeNull()

        ref.latitude shouldBe 50.0333
        ref.longitude.shouldNotBeNull() shouldBe (8.5706 plusOrMinus 1e-9)
    }

    @Test
    fun `a longitude in the western world copy is wrapped and kept`() {
        val ref = ref(icao = "PHNL", iata = "HNL", latitude = 21.3187, longitude = -517.9225).shouldNotBeNull()

        ref.longitude.shouldNotBeNull() shouldBe (-157.9225 plusOrMinus 1e-9)
    }
}
