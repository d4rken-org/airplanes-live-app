package eu.darken.apl.server.access

import eu.darken.apl.common.serialization.SerializationModule
import eu.darken.apl.server.ServerModule
import eu.darken.apl.server.api.AccessResponse
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.time.Instant

class AccessStateTest : BaseTest() {

    private val serverJson = ServerModule.serverJson()
    private val appJson = SerializationModule().json()

    private fun stateOf(response: String) = AccessState.from(
        response = serverJson.decodeFromString(AccessResponse.serializer(), response),
        installationId = INSTALLATION_ID,
        fetchedAt = Instant.ofEpochMilli(1_710_000_000_000L),
    )

    @Test
    fun `a server without watch lists sends neither limit nor the operation`() {
        val state = stateOf(accessResponse(limitsExtra = "", operations = """["search", "ar", "map", "watches"]"""))

        state.limits.maxActiveWatches.shouldBeNull()
        state.limits.maxStoredWatches.shouldBeNull()
        state.offersWatchList shouldBe false
    }

    @Test
    fun `a server with watch lists sends both limits and the operation`() {
        val state = stateOf(
            accessResponse(
                limitsExtra = """, "maxActiveWatches": 10, "maxStoredWatches": 50""",
                operations = """["search", "watches", "watchlist"]""",
            )
        )

        state.limits.maxActiveWatches shouldBe 10
        state.limits.maxStoredWatches shouldBe 50
        state.offersWatchList shouldBe true
    }

    @Test
    fun `limits without the operation do not offer watch lists`() {
        val state = stateOf(
            accessResponse(
                limitsExtra = """, "maxActiveWatches": 10, "maxStoredWatches": 50""",
                operations = """["search", "watches"]""",
            )
        )

        state.limits.maxActiveWatches shouldBe 10
        state.offersWatchList shouldBe false
    }

    @Test
    fun `the operation without limits still offers watch lists`() {
        val state = stateOf(accessResponse(limitsExtra = "", operations = """["watchlist"]"""))

        state.limits.maxActiveWatches.shouldBeNull()
        state.offersWatchList shouldBe true
    }

    @Test
    fun `a policy persisted before watch lists still decodes`() {
        val state = appJson.decodeFromString(AccessState.serializer(), PERSISTED_BEFORE_WATCH_LISTS)

        state.installationId shouldBe INSTALLATION_ID
        state.fetchedAt shouldBe Instant.ofEpochMilli(1_710_000_000_000L)
        state.tier shouldBe AccessState.Tier.FREE
        state.limits.watchesPerDay shouldBe 1000
        state.limits.maxActiveWatches.shouldBeNull()
        state.limits.maxStoredWatches.shouldBeNull()
        state.aircraftOperations shouldBe listOf("search", "ar", "map", "watches", "history_watches")
        state.offersWatchList shouldBe false
        state.maxWatches shouldBe AccessState.FREE_MAX_WATCHES
    }

    @Test
    fun `a persisted policy with watch list limits round trips`() {
        val state = stateOf(
            accessResponse(
                limitsExtra = """, "maxActiveWatches": 10, "maxStoredWatches": 50""",
                operations = """["watchlist"]""",
            )
        )

        val restored = appJson.decodeFromString(AccessState.serializer(), appJson.encodeToString(AccessState.serializer(), state))

        restored shouldBe state
        restored.offersWatchList shouldBe true
    }

    companion object {
        private const val INSTALLATION_ID = "5a5c6b2e-3b0a-4a2e-9a0f-000000000002"

        private fun accessResponse(limitsExtra: String, operations: String) = """
            {
              "tier": "free",
              "restricted": false,
              "allowanceScope": "principal",
              "limits": {
                "viewingIntervalSeconds": 5,
                "viewingBurst": 3,
                "viewingPerDay": 25000,
                "searchesPerDay": 25,
                "watchesPerDay": 1000,
                "maxRadiusNm": 25,
                "watchTypes": ["hex", "callsign"]$limitsExtra
              },
              "usage": {
                "resetsAt": 1710028800000,
                "viewing": {"limit": 25000, "used": 0, "reserved": 0, "remaining": 25000},
                "search": {"limit": 25, "used": 0, "reserved": 0, "remaining": 25},
                "watch": {"limit": 1000, "used": 0, "reserved": 0, "remaining": 1000}
              },
              "installationRequests": {
                "viewing": {"perSecond": 0.2, "burst": 3},
                "search": {"perSecond": 1.0, "burst": 3},
                "watch": {"perSecond": 1.0, "burst": 3},
                "concurrency": 3
              },
              "aircraftOperations": $operations
            }
        """.trimIndent()

        /** As the app stored it before the watch list fields existed: defaults written, nulls left out. */
        private val PERSISTED_BEFORE_WATCH_LISTS = """
            {"installationId":"$INSTALLATION_ID","fetchedAt":"2024-03-09T16:00:00Z","tier":"FREE","restricted":false,
            "allowanceScope":"principal","limits":{"viewingIntervalSeconds":5,"viewingBurst":3,"viewingPerDay":25000,
            "searchesPerDay":25,"watchesPerDay":1000,"concurrency":3,"maxIdentifierBatch":100,"maxRadiusNm":25,
            "maxArResults":300,"maxLocationWatchResults":100,"watchTypes":["hex","callsign"],"historyReplay":false},
            "usage":{"resetsAt":1710028800000,"viewing":{"limit":25000,"used":0,"reserved":0,"remaining":25000},
            "search":{"limit":25,"used":0,"reserved":0,"remaining":25},"watch":{"limit":1000,"used":0,"reserved":0,"remaining":1000}},
            "installationRequests":{"viewing":{"perSecond":0.2,"burst":3},"search":{"perSecond":1.0,"burst":3},
            "watch":{"perSecond":1.0,"burst":3},"concurrency":3},
            "aircraftOperations":["search","ar","map","watches","history_watches"]}
        """.trimIndent()
    }
}
