package eu.darken.apl.feeder.core

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class FeederWebLinksTest : BaseTest() {

    private val uuid = "4d3d2c9f-c0bd-4243-90d0-d913a0ac526a"

    @Test
    fun `short id is the last uuid group`() {
        uuid.toShortFeederId() shouldBe "d913a0ac526a"
    }

    @Test
    fun `short id accepts uppercase hex`() {
        "4D3D2C9F-C0BD-4243-90D0-D913A0AC526A".toShortFeederId() shouldBe "D913A0AC526A"
    }

    @Test
    fun `non-uuid ids pass through unchanged`() {
        // The website route accepts full UUIDs and 404s on garbage; slicing a malformed id
        // could fabricate something that looks like a valid short id.
        "not-a-uuid".toShortFeederId() shouldBe "not-a-uuid"
        "".toShortFeederId() shouldBe ""
        "4d3d2c9fc0bd424390d0d913a0ac526a".toShortFeederId() shouldBe "4d3d2c9fc0bd424390d0d913a0ac526a"
    }

    @Test
    fun `website url uses short id with trailing slash`() {
        feederWebsiteUrl("https://airplanes.live", uuid) shouldBe
            "https://airplanes.live/account/feeders/d913a0ac526a/"
    }

    @Test
    fun `website url tolerates trailing slash on host`() {
        feederWebsiteUrl("https://web.dev.airplanes.live/", uuid) shouldBe
            "https://web.dev.airplanes.live/account/feeders/d913a0ac526a/"
    }
}
