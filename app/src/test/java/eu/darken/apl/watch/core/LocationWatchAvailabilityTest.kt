package eu.darken.apl.watch.core

import eu.darken.apl.server.access.AccessState
import eu.darken.apl.server.session.SessionState
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class LocationWatchAvailabilityTest : BaseTest() {

    private val active = SessionState.Active("install-1")

    private fun access(
        installationId: String = "install-1",
        restricted: Boolean = false,
        watchTypes: Set<String> = setOf("aircraft", "squawk", "location"),
    ) = mockk<AccessState> {
        every { this@mockk.installationId } returns installationId
        every { this@mockk.restricted } returns restricted
        every { allowsWatchType(any()) } answers { firstArg<String>() in watchTypes }
    }

    @Test
    fun `a revoked session is revoked even with a cached policy that allows location watches`() {
        locationWatchAvailability(access(), SessionState.Revoked) shouldBe LocationWatchAvailability.REVOKED
        locationWatchAvailability(null, SessionState.Revoked) shouldBe LocationWatchAvailability.REVOKED
    }

    @Test
    fun `a session that is not active is unusable`() {
        listOf(SessionState.Unknown, SessionState.NoSession, SessionState.KeyLost).forEach { session ->
            locationWatchAvailability(access(), session) shouldBe LocationWatchAvailability.SESSION_UNUSABLE
            locationWatchAvailability(null, session) shouldBe LocationWatchAvailability.SESSION_UNUSABLE
        }
    }

    @Test
    fun `a restricted policy is restricted even when it allows location watches`() {
        locationWatchAvailability(access(restricted = true), active) shouldBe LocationWatchAvailability.RESTRICTED
    }

    @Test
    fun `without a policy availability is not loaded`() {
        locationWatchAvailability(null, active) shouldBe LocationWatchAvailability.NOT_LOADED
    }

    @Test
    fun `a policy for another installation is not loaded`() {
        locationWatchAvailability(access(installationId = "install-0"), active) shouldBe
            LocationWatchAvailability.NOT_LOADED
    }

    @Test
    fun `a policy without location watches excludes them by tier`() {
        locationWatchAvailability(access(watchTypes = setOf("aircraft", "squawk")), active) shouldBe
            LocationWatchAvailability.TIER_EXCLUDED
    }

    @Test
    fun `a matching policy with location watches makes them available`() {
        locationWatchAvailability(access(), active) shouldBe LocationWatchAvailability.AVAILABLE
    }
}
