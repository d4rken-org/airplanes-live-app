package eu.darken.apl.airport.core

import android.os.Parcel
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class AirportParcelTest {

    @Test
    fun `a full airport survives a parcel`() {
        val target = AirportTarget.Full(
            Airport(
                id = "oa:3",
                name = "Below Sea Level Airport",
                type = "small",
                ident = "XX-0003",
                latitude = 31.5,
                longitude = 35.5,
                elevationFt = -1266,
                scheduledService = false,
                website = "https://below-sea-level-airport.example/",
                wikipedia = "https://en.wikipedia.org/wiki/Below_Sea_Level_Airport",
            )
        )

        roundTrip(target) shouldBe target
    }

    @Test
    fun `a full airport keeps its time zone in a parcel`() {
        val target = AirportTarget.Full(
            Airport(
                id = "oa:2212",
                name = "Frankfurt Main Airport",
                type = "large",
                ident = "EDDF",
                latitude = 50.026706,
                longitude = 8.55835,
                timeZone = "Europe/Berlin",
            )
        )

        roundTrip(target) shouldBe target
    }

    @Test
    fun `a partial airport keeps catalogue id, elevation and time zone in a parcel`() {
        val target = AirportTarget.Partial(
            AirportRef.create(
                icao = "EDDF",
                iata = "FRA",
                name = "Frankfurt am Main Airport",
                latitude = 50.0333,
                longitude = 8.5706,
                id = "oa:2212",
                elevationFt = 364,
                timeZone = "Europe/Berlin",
            )!!
        )

        roundTrip(target) shouldBe target
    }

    private fun roundTrip(target: AirportTarget): AirportTarget? {
        val parcel = Parcel.obtain()
        return try {
            parcel.writeParcelable(target, 0)
            parcel.setDataPosition(0)
            @Suppress("DEPRECATION")
            parcel.readParcelable<AirportTarget>(AirportTarget::class.java.classLoader)
        } finally {
            parcel.recycle()
        }
    }
}
