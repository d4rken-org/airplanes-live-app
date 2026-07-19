package eu.darken.apl.account.core.api

import eu.darken.apl.common.serialization.SerializationModule
import eu.darken.apl.feeder.core.Feeder
import eu.darken.apl.feeder.core.FeederCache
import eu.darken.apl.feeder.core.FeederStatus
import eu.darken.apl.feeder.core.config.FeederPosition
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.time.Instant

/**
 * Guards the wire format of the account API and the on-device cache. Breaking either silently
 * breaks login / the feeder list, so these are pinned.
 */
class AccountModelsSerializationTest : BaseTest() {

    private val json = SerializationModule().json()

    @Test
    fun `identity decodes snake_case and nulls`() {
        val identity = json.decodeFromString<AccountApi.Identity>(
            """{"id":42,"email":"u@example.com","handle":null,"display_name":"Alice"}"""
        )
        identity.id shouldBe 42L
        identity.email shouldBe "u@example.com"
        identity.handle.shouldBeNull()
        identity.displayName shouldBe "Alice"
    }

    @Test
    fun `identity tolerates missing optional fields`() {
        val identity = json.decodeFromString<AccountApi.Identity>("""{"id":7}""")
        identity.id shouldBe 7L
        identity.email.shouldBeNull()
        identity.handle.shouldBeNull()
        identity.displayName.shouldBeNull()
    }

    @Test
    fun `owned feeder decodes full and sparse shapes`() {
        val resp = json.decodeFromString<AccountApi.FeedersResponse>(
            """
            {"feeders":[
                {"feeder_id":"uuid-1","name":"Roof","status":"active",
                 "last_seen":"2026-06-01T07:55:01.123456Z","country":"DE",
                 "position":{"lat":51.1,"lon":6.7},
                 "last_seen_ip":"94.114.50.178","first_seen":"2026-06-18T15:20:03.306435Z"},
                {"feeder_id":"uuid-2","status":"data-blocked"}
            ]}
            """.trimIndent()
        )
        resp.feeders.size shouldBe 2

        val full = resp.feeders[0]
        full.feederId shouldBe "uuid-1"
        full.name shouldBe "Roof"
        full.status shouldBe "active"
        full.lastSeen shouldBe Instant.parse("2026-06-01T07:55:01.123456Z")
        full.country shouldBe "DE"
        full.position?.lat shouldBe 51.1
        full.position?.lon shouldBe 6.7
        full.lastSeenIp shouldBe "94.114.50.178"
        full.firstSeen shouldBe Instant.parse("2026-06-18T15:20:03.306435Z")

        val sparse = resp.feeders[1]
        sparse.feederId shouldBe "uuid-2"
        sparse.name.shouldBeNull()
        sparse.lastSeen.shouldBeNull()
        sparse.position.shouldBeNull()
        // Pre-#242 server pods omit these entirely (rollout skew) — absent must read as null.
        sparse.lastSeenIp.shouldBeNull()
        sparse.firstSeen.shouldBeNull()
    }

    @Test
    fun `feeder cache round-trips (DataStore stored shape)`() {
        val cache = FeederCache(
            ownerId = 42L,
            feeders = listOf(
                Feeder(
                    id = "uuid-1",
                    name = "Roof",
                    status = FeederStatus.ACTIVE,
                    lastSeen = Instant.parse("2026-06-01T07:55:01Z"),
                    country = "DE",
                    position = FeederPosition(51.1, 6.7),
                    lastSeenIp = "94.114.50.178",
                    firstSeen = Instant.parse("2026-06-18T15:20:03Z"),
                ),
                Feeder(id = "uuid-2", status = FeederStatus.DATA_BLOCKED),
            ),
        )

        val restored = json.decodeFromString<FeederCache>(json.encodeToString(cache))
        restored shouldBe cache
    }

    @Test
    fun `feeder cache written before the identity fields still decodes`() {
        // Exact on-disk shape a pre-identity-fields build persisted; upgrading must not wipe it.
        val restored = json.decodeFromString<FeederCache>(
            """
            {"ownerId":42,"feeders":[
                {"id":"uuid-1","name":"Roof","status":"active",
                 "lastSeen":"2026-06-01T07:55:01Z","country":"DE",
                 "position":{"latitude":51.1,"longitude":6.7}}
            ]}
            """.trimIndent()
        )
        restored.ownerId shouldBe 42L
        restored.feeders.single() shouldBe Feeder(
            id = "uuid-1",
            name = "Roof",
            status = FeederStatus.ACTIVE,
            lastSeen = Instant.parse("2026-06-01T07:55:01Z"),
            country = "DE",
            position = FeederPosition(51.1, 6.7),
            lastSeenIp = null,
            firstSeen = null,
        )
    }

    @Test
    fun `feeder status enum serializes to api wire values`() {
        json.encodeToString(FeederStatus.DATA_BLOCKED) shouldBe "\"data-blocked\""
        json.decodeFromString<FeederStatus>("\"inactive\"") shouldBe FeederStatus.INACTIVE
    }

    @Test
    fun `empty feeder cache is the default`() {
        val restored = json.decodeFromString<FeederCache>("""{}""")
        restored.ownerId.shouldBeNull()
        restored.feeders shouldBe emptyList()
    }
}
