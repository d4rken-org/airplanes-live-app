package eu.darken.apl.ar.ui

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class ArViewModelRangeTest : BaseTest() {

    @Test
    fun `the tier's radius caps the range`() {
        ArViewModel.maxRangeNm(tierMaxNm = 25, apiMaxNm = 50.0) shouldBe 25
    }

    @Test
    fun `without a known tier the provider's default radius caps it`() {
        ArViewModel.maxRangeNm(tierMaxNm = null, apiMaxNm = 50.0) shouldBe 25
    }

    @Test
    fun `a tier above the slider's maximum leaves the maximum`() {
        ArViewModel.maxRangeNm(tierMaxNm = 250, apiMaxNm = 50.0) shouldBe 50
    }

    @Test
    fun `the range never drops below the minimum`() {
        ArViewModel.maxRangeNm(tierMaxNm = 0, apiMaxNm = 50.0) shouldBe 1
    }
}
