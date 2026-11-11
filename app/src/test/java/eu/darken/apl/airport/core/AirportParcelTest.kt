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

        val parcel = Parcel.obtain()
        val restored = try {
            parcel.writeParcelable(target, 0)
            parcel.setDataPosition(0)
            @Suppress("DEPRECATION")
            parcel.readParcelable<AirportTarget>(AirportTarget::class.java.classLoader)
        } finally {
            parcel.recycle()
        }

        restored shouldBe target
    }
}
