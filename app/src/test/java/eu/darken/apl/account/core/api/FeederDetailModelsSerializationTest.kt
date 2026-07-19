package eu.darken.apl.account.core.api

import eu.darken.apl.common.serialization.SerializationModule
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.time.Instant

/**
 * Pins the wire format of `GET api/v1/me/feeders/{feeder_id}`. The server omits empty families
 * and windows and sends null bucket values — every omission shape must decode.
 */
class FeederDetailModelsSerializationTest : BaseTest() {

    private val json = SerializationModule().json()

    @Test
    fun `full response decodes`() {
        val resp = json.decodeFromString<FeederDetailResponse>(
            """
            {
              "feeder_id": "0f2184c3-9715-4dbd-8e30-c9bbd1fd7548",
              "name": "Roof",
              "status": "active",
              "last_seen": "2026-07-19T12:34:56Z",
              "country": "DE",
              "position": {"lat": 52.5, "lon": 13.4},
              "live": {
                "feed": {"connected": true, "messages_per_sec": 123.4, "positions_per_sec": 12.3, "rtt_ms": 42, "connected_for_seconds": 3600},
                "mlat": {"connected": true, "peer_count": 7, "message_rate": 1.5, "outlier_percent": 0.4, "bad_sync": false, "interest_count": 30},
                "device": {"cpu_temperature_c": 55.5, "memory_used_percent": 41.0, "disk_used_percent": 22.5, "wifi_rssi_dbm": -63, "clock_skew_seconds": 0.1}
              },
              "history": {
                "24h": {
                  "start": "2026-07-18T12:24:00Z",
                  "end": "2026-07-19T12:48:00Z",
                  "bucket_seconds": 1440,
                  "series": {
                    "feed": {"messages_per_sec": [1.0, null, 3.0]},
                    "device": {"cpu_temperature_c": [50.0, 51.0, null]}
                  }
                },
                "30d": {
                  "start": "2026-06-19T12:00:00Z",
                  "end": "2026-07-19T12:00:00Z",
                  "bucket_seconds": 43200,
                  "series": {"mlat": {"peer_count": [5.0, 6.0]}}
                }
              }
            }
            """.trimIndent()
        )
        resp.feederId shouldBe "0f2184c3-9715-4dbd-8e30-c9bbd1fd7548"
        resp.live.shouldNotBeNull()
        resp.live!!.feed!!.messagesPerSec shouldBe 123.4
        resp.live!!.feed!!.rttMs shouldBe 42
        resp.live!!.mlat!!.badSync shouldBe false
        resp.live!!.device!!.wifiRssiDbm shouldBe -63
        resp.history shouldContainKey "24h"
        resp.history shouldContainKey "30d"
        val h24 = resp.history.getValue("24h")
        h24.start shouldBe Instant.parse("2026-07-18T12:24:00Z")
        h24.bucketSeconds shouldBe 1440
        h24.series.getValue("feed").getValue("messages_per_sec") shouldContainExactly listOf(1.0, null, 3.0)
    }

    @Test
    fun `device family omitted when diagnostics reporting off`() {
        val resp = json.decodeFromString<FeederDetailResponse>(
            """
            {
              "feeder_id": "abc",
              "live": {
                "feed": {"connected": false},
                "mlat": {"connected": false}
              },
              "history": {
                "24h": {"start": "2026-07-18T12:00:00Z", "end": "2026-07-19T12:00:00Z", "bucket_seconds": 1440,
                        "series": {"feed": {"messages_per_sec": [null, null]}}}
              }
            }
            """.trimIndent()
        )
        resp.live!!.device.shouldBeNull()
        resp.history.getValue("24h").series shouldNotContainKey "device"
    }

    @Test
    fun `windows and live block can be absent entirely`() {
        val resp = json.decodeFromString<FeederDetailResponse>("""{"feeder_id":"abc"}""")
        resp.live.shouldBeNull()
        resp.history shouldBe emptyMap()
    }

    @Test
    fun `unknown future family in history decodes without changes`() {
        val resp = json.decodeFromString<FeederDetailResponse>(
            """
            {
              "feeder_id": "abc",
              "history": {
                "24h": {"start": "2026-07-18T12:00:00Z", "end": "2026-07-19T12:00:00Z", "bucket_seconds": 1440,
                        "series": {"aircraft_stats": {"aircraft_count": [12.0, 15.0]}}}
              }
            }
            """.trimIndent()
        )
        resp.history.getValue("24h").series.getValue("aircraft_stats")
            .getValue("aircraft_count") shouldContainExactly listOf(12.0, 15.0)
    }

    @Test
    fun `unknown top-level and nested keys are ignored`() {
        val resp = json.decodeFromString<FeederDetailResponse>(
            """
            {
              "feeder_id": "abc",
              "brand_new_field": {"x": 1},
              "live": {"feed": {"connected": true, "brand_new_metric": 5}}
            }
            """.trimIndent()
        )
        resp.feederId shouldBe "abc"
        resp.live!!.feed!!.connected shouldBe true
    }

    @Test
    fun `round-trips through encode-decode`() {
        val original = FeederDetailResponse(
            feederId = "abc",
            live = FeederLiveWire(
                feed = FeederFeedWire(connected = true, messagesPerSec = 1.5),
                mlat = FeederMlatWire(connected = false, peerCount = 3),
                device = FeederDeviceWire(cpuTemperatureC = 60.0),
            ),
            history = mapOf(
                "24h" to FeederHistoryWindowWire(
                    start = Instant.parse("2026-07-18T12:00:00Z"),
                    end = Instant.parse("2026-07-19T12:00:00Z"),
                    bucketSeconds = 1440,
                    series = mapOf("feed" to mapOf("messages_per_sec" to listOf(1.0, null, 2.0))),
                ),
            ),
        )
        val decoded = json.decodeFromString<FeederDetailResponse>(json.encodeToString(FeederDetailResponse.serializer(), original))
        decoded shouldBe original
    }
}
