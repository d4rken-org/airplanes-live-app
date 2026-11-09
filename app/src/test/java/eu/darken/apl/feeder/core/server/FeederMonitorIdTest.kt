package eu.darken.apl.feeder.core.server

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class FeederMonitorIdTest : BaseTest() {

    @Test
    fun `a uuid in any case is lowercased and trimmed`() {
        "0A1B2C3D-4E5F-4A6B-8C7D-9E0F1A2B3C4D".toFeederMonitorId() shouldBe ID
        "  $ID\n".toFeederMonitorId() shouldBe ID
        ID.toFeederMonitorId() shouldBe ID
    }

    @Test
    fun `the all-zero uuid has no monitor id`() {
        "00000000-0000-0000-0000-000000000000".toFeederMonitorId() shouldBe null
    }

    @Test
    fun `anything but a hyphenated uuid has no monitor id`() {
        "".toFeederMonitorId() shouldBe null
        "not-a-uuid".toFeederMonitorId() shouldBe null
        "1-2-3-4-5".toFeederMonitorId() shouldBe null
        "0a1b2c3d4e5f4a6b8c7d9e0f1a2b3c4d".toFeederMonitorId() shouldBe null
        "{$ID}".toFeederMonitorId() shouldBe null
        "${ID}0".toFeederMonitorId() shouldBe null
    }

    companion object {
        private const val ID = "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d"
    }
}
