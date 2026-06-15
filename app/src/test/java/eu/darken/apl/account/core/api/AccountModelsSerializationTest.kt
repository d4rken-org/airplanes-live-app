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
                 "position":{"lat":51.1,"lon":6.7}},
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

        val sparse = resp.feeders[1]
        sparse.feederId shouldBe "uuid-2"
        sparse.name.shouldBeNull()
        sparse.lastSeen.shouldBeNull()
        sparse.position.shouldBeNull()
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
                ),
                Feeder(id = "uuid-2", status = FeederStatus.DATA_BLOCKED),
            ),
        )

        val restored = json.decodeFromString<FeederCache>(json.encodeToString(cache))
        restored shouldBe cache
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
