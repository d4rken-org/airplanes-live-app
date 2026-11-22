package eu.darken.apl.server

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class PrivacyAcceptanceTest : BaseTest() {

    @Test
    fun `isAccepted reports the current answer without waiting for acceptance`() = runTest {
        val accepted = MutableStateFlow(false)
        val acceptance = PrivacyAcceptance(accepted)

        acceptance.isAccepted() shouldBe false

        accepted.value = true
        acceptance.isAccepted() shouldBe true
    }
}
