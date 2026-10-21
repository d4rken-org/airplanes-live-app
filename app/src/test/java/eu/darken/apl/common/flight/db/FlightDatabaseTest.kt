package eu.darken.apl.common.flight.db

import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelper.coroutine.TestDispatcherProvider

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class FlightDatabaseTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var db: FlightDatabase

    @Before
    fun setup() {
        context.deleteDatabase("flights")
        db = FlightDatabase(context, TestDispatcherProvider())
    }

    @After
    fun teardown() {
        context.deleteDatabase("flights")
    }

    private suspend fun airport(icao: String) = db.airportByIcao(icao).first()!!

    @Test
    fun `an update without coordinates keeps the stored ones`(): Unit = runBlocking {
        db.upsertAirport("EGLL", "LHR", "London Heathrow Airport", "United Kingdom", 51.4706, -0.461941)
        db.upsertAirport("EGLL", null, null, null, null, null)

        val stored = airport("EGLL")
        stored.iataCode shouldBe "LHR"
        stored.name shouldBe "London Heathrow Airport"
        stored.latitude shouldBe 51.4706
        stored.longitude shouldBe -0.461941
    }

    @Test
    fun `a new pair replaces the stored coordinates`(): Unit = runBlocking {
        db.upsertAirport("EGLL", "LHR", null, null, 51.4706, -0.461941)
        db.upsertAirport("EGLL", null, null, null, 51.47, -0.4543)

        val stored = airport("EGLL")
        stored.latitude shouldBe 51.47
        stored.longitude shouldBe -0.4543
    }

    @Test
    fun `half a pair never mixes with the stored one`(): Unit = runBlocking {
        db.upsertAirport("EGLL", "LHR", null, null, 51.4706, -0.461941)
        db.upsertAirport("EGLL", null, null, null, 10.0, null)
        db.upsertAirport("EGLL", null, null, null, null, 10.0)

        val stored = airport("EGLL")
        stored.latitude shouldBe 51.4706
        stored.longitude shouldBe -0.461941
    }

    @Test
    fun `coordinates arrive for an airport first stored without them`(): Unit = runBlocking {
        db.upsertAirport("EGLL", null, null, null, null, null)
        db.upsertAirport("EGLL", "LHR", null, null, 51.4706, -0.461941)

        val stored = airport("EGLL")
        stored.iataCode shouldBe "LHR"
        stored.latitude shouldBe 51.4706
    }

    @Test
    fun `a new airport with half a pair gets no coordinates`(): Unit = runBlocking {
        db.upsertAirport("EGLL", "LHR", null, null, 51.4706, null)

        val stored = airport("EGLL")
        stored.latitude shouldBe null
        stored.longitude shouldBe null
    }
}
