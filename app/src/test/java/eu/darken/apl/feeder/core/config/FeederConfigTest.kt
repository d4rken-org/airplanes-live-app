package eu.darken.apl.feeder.core.config

import eu.darken.apl.common.serialization.SerializationModule
import eu.darken.apl.feeder.core.ReceiverId
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.time.Duration

class FeederConfigTest : BaseTest() {

    private val json = SerializationModule().json()

    @Test
    fun `FeederConfig has offline notifications disabled by default`() {
        val receiverId: ReceiverId = "test-receiver-id"
        val config = FeederConfig(receiverId = receiverId)

        config.offlineCheckTimeout shouldBe null
    }

    @Test
    fun `newly added feeders have offline notifications enabled with 48 hour timeout`() {
        val receiverId: ReceiverId = "test-receiver-id"
        val config = FeederConfig.newFeeder(receiverId)

        config.receiverId shouldBe receiverId
        config.offlineCheckTimeout shouldBe FeederConfig.DEFAULT_OFFLINE_LIMIT
        FeederConfig.DEFAULT_OFFLINE_LIMIT shouldBe Duration.ofHours(48)
    }

    @Test
    fun `FeederConfig can override default offline timeout`() {
        val receiverId: ReceiverId = "test-receiver-id"
        val customTimeout = Duration.ofHours(6)
        val config = FeederConfig(
            receiverId = receiverId,
            offlineCheckTimeout = customTimeout
        )

        config.offlineCheckTimeout shouldBe customTimeout
    }

    @Test
    fun `FeederConfig can disable offline notifications`() {
        val receiverId: ReceiverId = "test-receiver-id"
        val config = FeederConfig(
            receiverId = receiverId,
            offlineCheckTimeout = null
        )

        config.offlineCheckTimeout shouldBe null
    }

    @Test
    fun `disabled offline check survives serialization round trip`() {
        val original = FeederConfig(
            receiverId = "test-receiver-id",
            offlineCheckTimeout = null,
        )

        val restored = json.decodeFromString<FeederConfig>(json.encodeToString(original))

        restored.offlineCheckTimeout shouldBe null
    }

    @Test
    fun `enabled offline check survives serialization round trip`() {
        val original = FeederConfig(
            receiverId = "test-receiver-id",
            offlineCheckTimeout = Duration.ofHours(48),
        )

        val restored = json.decodeFromString<FeederConfig>(json.encodeToString(original))

        restored.offlineCheckTimeout shouldBe Duration.ofHours(48)
    }

    @Test
    fun `config without offlineCheckTimeout key decodes as disabled`() {
        val raw = """
            {
                "receiverId": "test-receiver-id",
                "addedAt": "2026-03-06T12:00:00Z",
                "label": "Rooftop"
            }
        """.trimIndent()

        val config = json.decodeFromString<FeederConfig>(raw)

        config.offlineCheckTimeout shouldBe null
    }
}