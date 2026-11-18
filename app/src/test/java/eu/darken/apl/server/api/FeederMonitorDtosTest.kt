package eu.darken.apl.server.api

import eu.darken.apl.server.ServerModule
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.SerializationException
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class FeederMonitorDtosTest : BaseTest() {

    private val json = ServerModule.serverJson()

    @Test
    fun `a history hour with only hour and final has no counts and no latency`() {
        val hour = json.decodeFromString<FeederHistoryHour>("""{"hour": 1759993200000, "final": true}""")

        hour.hour shouldBe 1_759_993_200_000L
        hour.isFinal shouldBe true
        hour.samples.shouldBeNull()
        hour.failures.shouldBeNull()
        hour.connectedSamples.shouldBeNull()
        hour.mlatSamples.shouldBeNull()
        hour.badSyncSamples.shouldBeNull()
        hour.reconnects.shouldBeNull()
        hour.rttAvgMs.shouldBeNull()
    }

    @Test
    fun `a history hour reads its average latency`() {
        val hour = json.decodeFromString<FeederHistoryHour>(
            """{"hour": 1759993200000, "samples": 12, "rttAvgMs": 48.75, "final": false}"""
        )

        hour.rttAvgMs shouldBe 48.75
        hour.samples shouldBe 12
        hour.isFinal shouldBe false
    }

    @Test
    fun `a history hour without hour or final does not decode`() {
        shouldThrow<SerializationException> {
            json.decodeFromString<FeederHistoryHour>("""{"samples": 12, "final": true}""")
        }
        shouldThrow<SerializationException> {
            json.decodeFromString<FeederHistoryHour>("""{"hour": 1759993200000, "samples": 12}""")
        }
    }

    @Test
    fun `latest reads its latency, zero included`() {
        json.decodeFromString<FeederMonitorLatest>("{}").rttMs.shouldBeNull()
        json.decodeFromString<FeederMonitorLatest>("""{"rttMs": 0}""").rttMs shouldBe 0L
        json.decodeFromString<FeederMonitorLatest>("""{"rttMs": 125}""").rttMs shouldBe 125L
    }

    @Test
    fun `a list entry without its monitor id does not decode`() {
        shouldThrow<SerializationException> {
            json.decodeFromString<FeederMonitorListResponse>(
                """
                {
                  "serverTime": 1760000000000,
                  "revision": 1,
                  "state": "active",
                  "limits": {"maxActiveFeeders": 3, "maxStoredFeeders": 20},
                  "feeders": [
                    {"feederId": "$FEEDER_ID", "offlineAfterMinutes": 60, "status": "active",
                     "createdAt": 1759000000000, "updatedAt": 1759000000000, "health": "online", "eventSeq": 0}
                  ],
                  "cursor": 0
                }
                """.trimIndent()
            )
        }
    }

    @Test
    fun `an events response without its cursor does not decode`() {
        shouldThrow<SerializationException> {
            json.decodeFromString<FeederMonitorEventsResponse>(
                """{"serverTime": 1760000000000, "events": [], "hasMore": false, "revision": 1}"""
            )
        }
    }

    companion object {
        private const val FEEDER_ID = "0f3a6c1e-8a51-4c5e-9e2b-7d6b1a2c3d4e"
    }
}
