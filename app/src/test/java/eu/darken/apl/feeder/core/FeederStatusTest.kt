package eu.darken.apl.feeder.core

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class FeederStatusTest : BaseTest() {

    @Test
    fun `maps known API values`() {
        FeederStatus.fromApi("active") shouldBe FeederStatus.ACTIVE
        FeederStatus.fromApi("inactive") shouldBe FeederStatus.INACTIVE
        FeederStatus.fromApi("idle") shouldBe FeederStatus.IDLE
        FeederStatus.fromApi("data-blocked") shouldBe FeederStatus.DATA_BLOCKED
    }

    @Test
    fun `is case and whitespace tolerant`() {
        FeederStatus.fromApi(" Active ") shouldBe FeederStatus.ACTIVE
        FeederStatus.fromApi("DATA_BLOCKED") shouldBe FeederStatus.DATA_BLOCKED
    }

    @Test
    fun `unknown or null maps to UNKNOWN`() {
        FeederStatus.fromApi(null) shouldBe FeederStatus.UNKNOWN
        FeederStatus.fromApi("") shouldBe FeederStatus.UNKNOWN
        FeederStatus.fromApi("weird-future-state") shouldBe FeederStatus.UNKNOWN
    }
}
