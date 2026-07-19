package eu.darken.apl.feeder.core.monitor

import eu.darken.apl.common.serialization.SerializationModule
import eu.darken.apl.feeder.core.FeederStatus
import eu.darken.apl.feeder.core.stats.HealthMetric
import eu.darken.apl.feeder.core.stats.HealthSeverity
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest

/** Pins the persisted DataStore shape of the monitor state — breaking it resets user muting/outage tracking. */
class FeederMonitorStateSerializationTest : BaseTest() {

    private val json = SerializationModule().json()

    @Test
    fun `round-trips fully populated state`() {
        val state = FeederMonitorState(
            feeders = mapOf(
                "feeder-a" to FeederTransitionState(
                    lastStatus = FeederStatus.INACTIVE,
                    outageActive = true,
                    offlineNotificationPosted = true,
                    healthWarnings = mapOf(HealthMetric.CPU_TEMPERATURE to HealthSeverity.CRIT),
                ),
            ),
        )
        val decoded = json.decodeFromString<FeederMonitorState>(json.encodeToString(FeederMonitorState.serializer(), state))
        decoded shouldBe state
    }

    @Test
    fun `empty json decodes to defaults`() {
        json.decodeFromString<FeederMonitorState>("{}") shouldBe FeederMonitorState()
        json.decodeFromString<FeederTransitionState>("{}") shouldBe FeederTransitionState()
    }

    @Test
    fun `pinned wire format`() {
        val decoded = json.decodeFromString<FeederMonitorState>(
            """
            {
              "feeders": {
                "x": {
                  "lastStatus": "inactive",
                  "outageActive": true,
                  "offlineNotificationPosted": false,
                  "healthWarnings": {"disk_available": "warn"}
                }
              }
            }
            """.trimIndent()
        )
        val state = decoded.feeders.getValue("x")
        state.lastStatus shouldBe FeederStatus.INACTIVE
        state.outageActive shouldBe true
        state.offlineNotificationPosted shouldBe false
        state.healthWarnings shouldBe mapOf(HealthMetric.DISK_AVAILABLE to HealthSeverity.WARN)
    }
}
