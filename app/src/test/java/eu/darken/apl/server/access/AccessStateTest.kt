package eu.darken.apl.server.access

import eu.darken.apl.common.serialization.SerializationModule
import eu.darken.apl.server.ServerModule
import eu.darken.apl.server.api.AccessResponse
import eu.darken.apl.server.api.Allowance
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
    fun `route lookup is offered when the server lists the operation`() {
        val state = stateOf(accessResponse(limitsExtra = "", operations = """["search", "map", "route"]"""))

        state.offersRouteLookup shouldBe true
    }

    @Test
    fun `route lookup is not offered without the operation`() {
        val state = stateOf(accessResponse(limitsExtra = "", operations = """["search", "ar", "map", "watches"]"""))

        state.offersRouteLookup shouldBe false
    }

    @Test
    fun `a server with feeder monitoring sends both limits and the operation`() {
        val state = stateOf(
            accessResponse(
                limitsExtra = """, "maxActiveFeederMonitors": 3, "maxStoredFeederMonitors": 20""",
                operations = """["search", "watchlist", "feeder_monitor"]""",
            )
        )

        state.limits.maxActiveFeederMonitors shouldBe 3
        state.limits.maxStoredFeederMonitors shouldBe 20
        state.offersFeederMonitor shouldBe true
    }

    @Test
    fun `a server without feeder monitoring sends neither limit nor the operation`() {
        val state = stateOf(accessResponse(limitsExtra = "", operations = """["search", "ar", "map", "watches"]"""))

        state.limits.maxActiveFeederMonitors.shouldBeNull()
        state.limits.maxStoredFeederMonitors.shouldBeNull()
        state.offersFeederMonitor shouldBe false
    }

    @Test
    fun `a policy persisted before feeder monitoring still decodes`() {
        val state = appJson.decodeFromString(AccessState.serializer(), PERSISTED_BEFORE_WATCH_LISTS)

        state.limits.maxActiveFeederMonitors.shouldBeNull()
        state.limits.maxStoredFeederMonitors.shouldBeNull()
        state.offersFeederMonitor shouldBe false
    }

    @Test
    fun `a persisted policy with feeder monitoring limits round trips`() {
        val state = stateOf(
            accessResponse(
                limitsExtra = """, "maxActiveFeederMonitors": 20, "maxStoredFeederMonitors": 20""",
                operations = """["feeder_monitor"]""",
            )
        )

        val restored = appJson.decodeFromString(AccessState.serializer(), appJson.encodeToString(AccessState.serializer(), state))

        restored shouldBe state
        restored.limits.maxActiveFeederMonitors shouldBe 20
        restored.offersFeederMonitor shouldBe true
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
    }

    @Test
    fun `a persisted policy keeps the daily limits and allowances it was stored with`() {
        val state = appJson.decodeFromString(AccessState.serializer(), PERSISTED_BEFORE_WATCH_LISTS)

        state.limits.viewingBurst shouldBe 3
        state.limits.viewingPerDay shouldBe 25000
        state.limits.searchesPerDay shouldBe 25
        state.limits.watchesPerDay shouldBe 1000
        state.usage.viewing shouldBe Allowance(limit = 25000, used = 0, reserved = 0, remaining = 25000)
        state.usage.search shouldBe Allowance(limit = 25, used = 0, reserved = 0, remaining = 25)
        state.usage.watch shouldBe Allowance(limit = 1000, used = 0, reserved = 0, remaining = 1000)
    }

    @Test
    fun `a policy without the daily limits and allowance totals round trips`() {
        val state = stateOf(accessResponse(limitsExtra = "", operations = """["search"]""", unusedFields = false))

        val restored = appJson.decodeFromString(AccessState.serializer(), appJson.encodeToString(AccessState.serializer(), state))

        restored shouldBe state
        restored.limits.apply {
            viewingBurst.shouldBeNull()
            viewingPerDay.shouldBeNull()
            searchesPerDay.shouldBeNull()
            watchesPerDay.shouldBeNull()
        }
        restored.usage.viewing shouldBe Allowance(limit = null, used = 0, reserved = null, remaining = 25000)
        restored.usage.search shouldBe Allowance(limit = null, used = 0, reserved = null, remaining = 25)
        restored.usage.watch shouldBe Allowance(limit = null, used = 0, reserved = null, remaining = 1000)
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

    @Test
    fun `the feeder tier is mapped`() {
        stateOf(accessResponse(limitsExtra = "", operations = "[]", tier = "feeder")).tier shouldBe AccessState.Tier.FEEDER
    }

    @Test
    fun `the subscriber tier is mapped`() {
        stateOf(accessResponse(limitsExtra = "", operations = "[]", tier = "subscriber")).tier shouldBe
                AccessState.Tier.SUBSCRIBER
    }

    @Test
    fun `an unknown tier unlocks nothing`() {
        stateOf(accessResponse(limitsExtra = "", operations = "[]", tier = "gold")).tier shouldBe AccessState.Tier.FREE
    }

    @Test
    fun `feeder and subscriber are pro, free is not`() {
        AccessState.Tier.FREE.isPro shouldBe false
        AccessState.Tier.FEEDER.isPro shouldBe true
        AccessState.Tier.SUBSCRIBER.isPro shouldBe true
    }

    @Test
    fun `only the free tier is shown its allowances`() {
        stateOf(accessResponse(limitsExtra = "", operations = "[]", tier = "free")).showsAllowances shouldBe true
        stateOf(accessResponse(limitsExtra = "", operations = "[]", tier = "feeder")).showsAllowances shouldBe false
        stateOf(accessResponse(limitsExtra = "", operations = "[]", tier = "subscriber")).showsAllowances shouldBe false
    }

    @Test
    fun `a persisted subscriber policy round trips`() {
        val state = stateOf(
            accessResponse(
                limitsExtra = "",
                operations = """["search"]""",
                tier = "subscriber",
                allowanceScope = "subscription",
            )
        )

        val restored = appJson.decodeFromString(AccessState.serializer(), appJson.encodeToString(AccessState.serializer(), state))

        restored shouldBe state
        restored.tier shouldBe AccessState.Tier.SUBSCRIBER
        restored.allowanceScope shouldBe "subscription"
    }

    private fun subscriptionOf(subscription: String?) =
        stateOf(accessResponse(limitsExtra = "", operations = "[]", subscription = subscription)).subscription

    @Test
    fun `each known subscription state is mapped with its expiry`() {
        mapOf(
            "active" to AccessState.SubscriptionStatus.State.ACTIVE,
            "in_grace" to AccessState.SubscriptionStatus.State.IN_GRACE,
            "canceled" to AccessState.SubscriptionStatus.State.CANCELED,
            "on_hold" to AccessState.SubscriptionStatus.State.ON_HOLD,
            "paused" to AccessState.SubscriptionStatus.State.PAUSED,
            "pending" to AccessState.SubscriptionStatus.State.PENDING,
        ).forEach { (wire, state) ->
            subscriptionOf("""{"state": "$wire", "expiresAt": 1795400000000}""") shouldBe
                    AccessState.SubscriptionStatus(state = state, expiresAt = 1_795_400_000_000L)
        }
    }

    @Test
    fun `an explicit null expiry decodes to no expiry`() {
        subscriptionOf("""{"state": "active", "expiresAt": null}""") shouldBe
                AccessState.SubscriptionStatus(state = AccessState.SubscriptionStatus.State.ACTIVE, expiresAt = null)
    }

    @Test
    fun `a missing expiry decodes to no expiry`() {
        subscriptionOf("""{"state": "on_hold"}""") shouldBe
                AccessState.SubscriptionStatus(state = AccessState.SubscriptionStatus.State.ON_HOLD, expiresAt = null)
    }

    @Test
    fun `the unknown subscription state maps to no status`() {
        subscriptionOf("""{"state": "unknown", "expiresAt": 1795400000000}""").shouldBeNull()
    }

    @Test
    fun `an unrecognized subscription state maps to no status`() {
        subscriptionOf("""{"state": "grace_v2", "expiresAt": 1795400000000}""").shouldBeNull()
    }

    @Test
    fun `no subscription object maps to no status`() {
        subscriptionOf(null).shouldBeNull()
    }

    @Test
    fun `a persisted policy with a subscription status round trips`() {
        val state = stateOf(
            accessResponse(
                limitsExtra = "",
                operations = """["search"]""",
                tier = "subscriber",
                subscription = """{"state": "canceled", "expiresAt": 1795400000000}""",
            )
        )

        val restored = appJson.decodeFromString(AccessState.serializer(), appJson.encodeToString(AccessState.serializer(), state))

        restored shouldBe state
        restored.subscription shouldBe AccessState.SubscriptionStatus(
            state = AccessState.SubscriptionStatus.State.CANCELED,
            expiresAt = 1_795_400_000_000L,
        )
    }

    @Test
    fun `a persisted policy with a subscription status but no expiry round trips`() {
        val state = stateOf(
            accessResponse(
                limitsExtra = "",
                operations = """["search"]""",
                subscription = """{"state": "pending", "expiresAt": null}""",
            )
        )

        val restored = appJson.decodeFromString(AccessState.serializer(), appJson.encodeToString(AccessState.serializer(), state))

        restored shouldBe state
        restored.subscription shouldBe AccessState.SubscriptionStatus(
            state = AccessState.SubscriptionStatus.State.PENDING,
            expiresAt = null,
        )
    }

    @Test
    fun `a policy persisted before the subscription status decodes without one`() {
        appJson.decodeFromString(AccessState.serializer(), PERSISTED_BEFORE_WATCH_LISTS).subscription.shouldBeNull()
    }

    companion object {
        private const val INSTALLATION_ID = "5a5c6b2e-3b0a-4a2e-9a0f-000000000002"

        private fun accessResponse(
            limitsExtra: String,
            operations: String,
            unusedFields: Boolean = true,
            tier: String = "free",
            allowanceScope: String = "principal",
            subscription: String? = null,
        ): String {
            val subscriptionField = subscription?.let { """, "subscription": $it""" } ?: ""

            val dailyLimits = if (unusedFields) {
                """ "viewingBurst": 3, "viewingPerDay": 25000, "searchesPerDay": 25, "watchesPerDay": 1000,"""
            } else {
                ""
            }

            fun allowance(limit: Int) = if (unusedFields) {
                """{"limit": $limit, "used": 0, "reserved": 0, "remaining": $limit}"""
            } else {
                """{"used": 0, "remaining": $limit}"""
            }

            return """
                {
                  "tier": "$tier",
                  "restricted": false,
                  "allowanceScope": "$allowanceScope",
                  "limits": {
                    "viewingIntervalSeconds": 5,$dailyLimits
                    "maxRadiusNm": 25,
                    "watchTypes": ["hex", "callsign"]$limitsExtra
                  },
                  "usage": {
                    "resetsAt": 1710028800000,
                    "viewing": ${allowance(25000)},
                    "search": ${allowance(25)},
                    "watch": ${allowance(1000)}
                  },
                  "installationRequests": {
                    "viewing": {"perSecond": 0.2, "burst": 3},
                    "search": {"perSecond": 1.0, "burst": 3},
                    "watch": {"perSecond": 1.0, "burst": 3},
                    "concurrency": 3
                  },
                  "aircraftOperations": $operations$subscriptionField
                }
            """.trimIndent()
        }

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
