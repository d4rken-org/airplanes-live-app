package eu.darken.apl.feeder.core.api

import eu.darken.apl.common.serialization.SerializationModule
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.util.UUID

class FeedInfosTest : BaseTest() {

    private val json: Json = SerializationModule().json()

    /** Trimmed capture of a live `GET /feed?id=...` answer. */
    private val live = """
        {
          "beast_clients": [
            {
              "uuid": "235c2662-9fbe-40db-a295-e42349e924a4",
              "host": "198.51.100.7", "port": 12345, "ingest_id": "in-1",
              "avg_kbit_s": 12.5, "conn_time": 3600, "msgs_s": 42.5, "pos_s": 1.25,
              "reduce": 1, "rtt": 30, "pos": 17
            }
          ],
          "mlat_clients": [
            {
              "uuid": "235c2662-9fbe-40db-a295-e42349e924a4",
              "user": "T-EDKA146", "privacy": false,
              "host": "198.51.100.7", "port": 23456, "ingest_id": "in-1",
              "lat": 50.8, "lon": 6.06, "alt": 180.0,
              "message_rate": 3.5, "peer_count": 12, "bad_sync_timeout": 0,
              "outlier_percent": 0.2,
              "bad_peer_list": [],
              "sync_interest": ["471dc1"],
              "mlat_interest": ["3c65a3"]
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `a live feed answer deserializes`() {
        val infos = json.decodeFromString(FeedInfos.serializer(), live)

        infos.beast.single().apply {
            uuid shouldBe UUID.fromString("235c2662-9fbe-40db-a295-e42349e924a4")
            messageRate shouldBe 42.5
            positions shouldBe 17
        }
        infos.mlat.single().apply {
            user shouldBe "T-EDKA146"
            peerCount shouldBe 12
            // The upstream sends this as an array; typing it as a string failed the whole response
            badPeerList shouldBe emptyList()
            syncInterest shouldBe listOf("471dc1")
        }
    }

    @Test
    fun `a populated bad peer list is read as entries`() {
        val withPeers = live.replace(""""bad_peer_list": [],""", """"bad_peer_list": ["a1b2c3", "d4e5f6"],""")

        val infos = json.decodeFromString(FeedInfos.serializer(), withPeers)

        infos.mlat.single().badPeerList shouldBe listOf("a1b2c3", "d4e5f6")
    }
}
