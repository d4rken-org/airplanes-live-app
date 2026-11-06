package eu.darken.apl.ar.core

import eu.darken.apl.common.flight.FlightRoute
import eu.darken.apl.common.flight.RouteLookupResult
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.time.Instant

class ArRouteRetriesTest : BaseTest() {

    private fun createRetries() = ArRouteRetries(maxAttempts = 3)

    private val found = RouteLookupResult.Found(
        FlightRoute(callsign = "DLH123", origin = null, destination = null, seenAt = Instant.EPOCH)
    )

    @Test
    fun `first lookup may start`() {
        createRetries().tryStart("AABBCC", nowMs = 0L) shouldBe true
    }

    @Test
    fun `no second start while in flight`() {
        val retries = createRetries()
        retries.tryStart("AABBCC", nowMs = 0L) shouldBe true
        retries.tryStart("AABBCC", nowMs = 1_000L) shouldBe false
        retries.tryStart("AABBCC", nowMs = 1_000_000L) shouldBe false
    }

    @Test
    fun `unavailable is retried from its deadline on`() {
        val retries = createRetries()
        retries.tryStart("AABBCC", nowMs = 0L)
        retries.onResult("AABBCC", RouteLookupResult.Unavailable(retryAtElapsed = 5_000L))

        retries.tryStart("AABBCC", nowMs = 4_999L) shouldBe false
        retries.tryStart("AABBCC", nowMs = 5_000L) shouldBe true
    }

    @Test
    fun `unavailable is retried after its deadline`() {
        val retries = createRetries()
        retries.tryStart("AABBCC", nowMs = 0L)
        retries.onResult("AABBCC", RouteLookupResult.Unavailable(retryAtElapsed = 5_000L))

        retries.tryStart("AABBCC", nowMs = 60_000L) shouldBe true
    }

    @Test
    fun `at most three attempts per hex`() {
        val retries = createRetries()
        retries.tryStart("AABBCC", nowMs = 0L) shouldBe true
        retries.onResult("AABBCC", RouteLookupResult.Unavailable(retryAtElapsed = 1_000L))
        retries.tryStart("AABBCC", nowMs = 1_000L) shouldBe true
        retries.onResult("AABBCC", RouteLookupResult.Unavailable(retryAtElapsed = 2_000L))
        retries.tryStart("AABBCC", nowMs = 2_000L) shouldBe true
        retries.onResult("AABBCC", RouteLookupResult.Unavailable(retryAtElapsed = 3_000L))

        retries.tryStart("AABBCC", nowMs = 3_000L) shouldBe false
        retries.tryStart("AABBCC", nowMs = 1_000_000L) shouldBe false
    }

    @Test
    fun `found is never retried`() {
        val retries = createRetries()
        retries.tryStart("AABBCC", nowMs = 0L)
        retries.onResult("AABBCC", found)

        retries.tryStart("AABBCC", nowMs = 1_000_000L) shouldBe false
    }

    @Test
    fun `none is never retried`() {
        val retries = createRetries()
        retries.tryStart("AABBCC", nowMs = 0L)
        retries.onResult("AABBCC", RouteLookupResult.None)

        retries.tryStart("AABBCC", nowMs = 1_000_000L) shouldBe false
    }

    @Test
    fun `none after an unavailable attempt ends the retries`() {
        val retries = createRetries()
        retries.tryStart("AABBCC", nowMs = 0L)
        retries.onResult("AABBCC", RouteLookupResult.Unavailable(retryAtElapsed = 1_000L))
        retries.tryStart("AABBCC", nowMs = 1_000L) shouldBe true
        retries.onResult("AABBCC", RouteLookupResult.None)

        retries.tryStart("AABBCC", nowMs = 1_000_000L) shouldBe false
    }

    @Test
    fun `hexes are tracked independently`() {
        val retries = createRetries()
        retries.tryStart("AAA", nowMs = 0L) shouldBe true
        retries.tryStart("BBB", nowMs = 0L) shouldBe true
        retries.onResult("AAA", RouteLookupResult.None)
        retries.onResult("BBB", RouteLookupResult.Unavailable(retryAtElapsed = 5_000L))

        retries.tryStart("CCC", nowMs = 1_000L) shouldBe true
        retries.tryStart("AAA", nowMs = 5_000L) shouldBe false
        retries.tryStart("BBB", nowMs = 5_000L) shouldBe true
    }
}
