package eu.darken.apl.map.ui

import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class MapCreditRoomTest : BaseTest() {

    @Test
    fun `without a status the credit may reach the room's bottom`() {
        mapCreditRoom(statusRoom = 400.dp, creditTop = 16.dp, statusHeight = 0.dp) shouldBe 376.dp
    }

    @Test
    fun `a status leaves the credit only the room above its top`() {
        // The status' top sits at 200 - 24 - 48 = 128dp
        mapCreditRoom(statusRoom = 200.dp, creditTop = 100.dp, statusHeight = 48.dp) shouldBe 20.dp
    }

    @Test
    fun `a short room with a status doesn't fit the credit, the status hiding does`() {
        val creditHeight = 30.dp
        val withStatus = mapCreditRoom(statusRoom = 200.dp, creditTop = 100.dp, statusHeight = 48.dp)
        (creditHeight <= withStatus) shouldBe false

        val withoutStatus = mapCreditRoom(statusRoom = 200.dp, creditTop = 100.dp, statusHeight = 0.dp)
        (creditHeight <= withoutStatus) shouldBe true
    }

    @Test
    fun `taller chips above push the credit out`() {
        mapCreditRoom(statusRoom = 200.dp, creditTop = 16.dp + 48.dp + 8.dp, statusHeight = 48.dp) shouldBe 48.dp
        mapCreditRoom(statusRoom = 200.dp, creditTop = 16.dp + 96.dp + 8.dp, statusHeight = 48.dp) shouldBe 0.dp
    }

    @Test
    fun `the room is never negative`() {
        mapCreditRoom(statusRoom = 50.dp, creditTop = 120.dp, statusHeight = 48.dp) shouldBe 0.dp
        mapCreditRoom(statusRoom = (-20).dp, creditTop = 16.dp, statusHeight = 0.dp) shouldBe 0.dp
    }
}
