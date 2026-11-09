package eu.darken.apl.feeder.core.server

import eu.darken.apl.feeder.core.config.FeederConfig
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.time.Duration
import java.time.Instant

class DesiredFeederMonitorTest : BaseTest() {

    private fun config(receiverId: String, addedAt: Long, timeout: Duration? = Duration.ofHours(48)) = FeederConfig(
        receiverId = receiverId,
        addedAt = Instant.ofEpochSecond(addedAt),
        offlineCheckTimeout = timeout,
    )

    @Test
    fun `feeders are ordered by when they were added, ties by feeder id`() {
        DesiredFeederMonitor.from(
            listOf(
                config(ID_C, addedAt = 30),
                config(ID_B, addedAt = 10),
                config(ID_A, addedAt = 10),
            )
        ).map { it.feederId } shouldBe listOf(ID_A, ID_B, ID_C)
    }

    @Test
    fun `thresholds are in minutes within the server's range, no offline alert asks for the longest`() {
        DesiredFeederMonitor.from(
            listOf(
                config(ID_A, addedAt = 1, timeout = Duration.ofHours(48)),
                config(ID_B, addedAt = 2, timeout = Duration.ofHours(6)),
                config(ID_C, addedAt = 3, timeout = null),
                config(ID_D, addedAt = 4, timeout = Duration.ofMinutes(5)),
                config(ID_E, addedAt = 5, timeout = Duration.ofDays(7)),
            )
        ).map { it.offlineAfterMinutes } shouldBe listOf(2880, 360, 2880, 15, 2880)
    }

    @Test
    fun `of case variants of one feeder the earliest added counts`() {
        DesiredFeederMonitor.from(
            listOf(
                config(ID_A, addedAt = 20, timeout = Duration.ofHours(6)),
                config(ID_A.uppercase(), addedAt = 10, timeout = Duration.ofHours(1)),
            )
        ) shouldBe listOf(DesiredFeederMonitor(ID_A, 60))
    }

    @Test
    fun `ids the server cannot take are left out`() {
        DesiredFeederMonitor.from(
            listOf(
                config("not-a-uuid", addedAt = 1),
                config("00000000-0000-0000-0000-000000000000", addedAt = 2),
                config(" ${ID_A.uppercase()} ", addedAt = 3),
            )
        ).map { it.feederId } shouldBe listOf(ID_A)
    }

    companion object {
        private const val ID_A = "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d"
        private const val ID_B = "1a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d"
        private const val ID_C = "2a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d"
        private const val ID_D = "3a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d"
        private const val ID_E = "4a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d"
    }
}
