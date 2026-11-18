package eu.darken.apl.feeder.core.server

import eu.darken.apl.feeder.core.config.FeederConfig
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.time.Instant

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

    private fun config(receiverId: String, addedAt: Long, mlat: Boolean = false) = FeederConfig(
        receiverId = receiverId,
        addedAt = Instant.ofEpochSecond(addedAt),
        notifyMlatProblems = mlat,
    )

    @Test
    fun `of conflicting variants of one id the earliest added counts`() {
        val earliest = config(ID.uppercase(), addedAt = 10, mlat = true)
        val later = config(ID, addedAt = 20, mlat = false)

        listOf(later, earliest).effectiveByFeederMonitorId() shouldBe mapOf(ID to earliest)
    }

    @Test
    fun `equally old variants of one id resolve by receiver id whatever their order`() {
        val upper = config(ID.uppercase(), addedAt = 10, mlat = true)
        val lower = config(ID, addedAt = 10, mlat = false)

        listOf(lower, upper).effectiveByFeederMonitorId() shouldBe mapOf(ID to upper)
        listOf(upper, lower).effectiveByFeederMonitorId() shouldBe mapOf(ID to upper)
    }

    @Test
    fun `without the earliest variant the next earliest counts`() {
        val earliest = config(ID.uppercase(), addedAt = 10, mlat = true)
        val next = config(" $ID", addedAt = 20, mlat = false)
        val last = config(ID, addedAt = 30, mlat = true)

        listOf(earliest, next, last).effectiveByFeederMonitorId() shouldBe mapOf(ID to earliest)
        listOf(next, last).effectiveByFeederMonitorId() shouldBe mapOf(ID to next)
    }

    @Test
    fun `effective configs are in registration order and leave out ids without a canonical form`() {
        val second = config(OTHER_ID, addedAt = 20)
        val first = config(ID, addedAt = 10)

        listOf(config("not-a-uuid", addedAt = 1), second, first)
            .effectiveByFeederMonitorId()
            .toList() shouldBe listOf(ID to first, OTHER_ID to second)
    }

    companion object {
        private const val ID = "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d"
        private const val OTHER_ID = "1a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d"
    }
}
