package eu.darken.apl.common

import eu.darken.apl.BuildConfig
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class SponsorHelperTest : BaseTest() {

    private val webpageTool = mockk<WebpageTool>(relaxed = true)

    @Test
    fun `foss opens the sponsor page`() = runTest {
        val helper = SponsorHelper(webpageTool, BuildConfigWrap.Flavor.FOSS)

        helper.isAvailable shouldBe true
        helper.openSponsorPage()

        verify(exactly = 1) { webpageTool.open("https://github.com/sponsors/d4rken") }
    }

    @Test
    fun `gplay refuses to open the sponsor page`() = runTest {
        val helper = SponsorHelper(webpageTool, BuildConfigWrap.Flavor.GPLAY)

        helper.isAvailable shouldBe false
        helper.openSponsorPage()

        verify(exactly = 0) { webpageTool.open(any()) }
    }

    @Test
    fun `unknown flavor refuses to open the sponsor page`() = runTest {
        val helper = SponsorHelper(webpageTool, BuildConfigWrap.Flavor.NONE)

        helper.isAvailable shouldBe false
        helper.openSponsorPage()

        verify(exactly = 0) { webpageTool.open(any()) }
    }

    // Runs under each flavor's unit test task, the gplay run checks the real gplay wiring
    @Test
    fun `injected helper follows the built flavor`() = runTest {
        val isFoss = BuildConfig.FLAVOR == "foss"
        val helper = SponsorHelper(webpageTool)

        helper.isAvailable shouldBe isFoss
        helper.openSponsorPage()

        if (isFoss) {
            verify(exactly = 1) { webpageTool.open("https://github.com/sponsors/d4rken") }
        } else {
            verify(exactly = 0) { webpageTool.open(any()) }
        }
    }
}
