package eu.darken.apl.map.ui

import eu.darken.apl.map.ui.NativeMapViewModel.CapAltitude
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class NativeMapViewModelCapTest : BaseTest() {

    private fun aboveFeet(cutoffFeet: Double?, capped: Boolean = true): Int? =
        NativeMapViewModel.capAltitude(capped, cutoffFeet, shownCount = 2000, totalMatching = 5000)?.aboveFeet

    @Test
    fun `a cutoff between thousands rounds down to the thousand below`() {
        aboveFeet(24_125.0) shouldBe 24_000
    }

    @Test
    fun `a cutoff on a whole thousand rounds down to the thousand below it`() {
        aboveFeet(24_000.0) shouldBe 23_000
    }

    @Test
    fun `a cutoff just above 1,000 ft shows 1,000 ft`() {
        aboveFeet(1_000.5) shouldBe 1_000
    }

    @Test
    fun `a cutoff of exactly 1,000 ft gives no altitude`() {
        aboveFeet(1_000.0) shouldBe null
    }

    @Test
    fun `a cutoff below 1,000 ft gives no altitude`() {
        aboveFeet(999.0) shouldBe null
    }

    @Test
    fun `no cutoff gives no altitude`() {
        aboveFeet(null) shouldBe null
    }

    @Test
    fun `an answer that is not capped gives no altitude`() {
        aboveFeet(24_125.0, capped = false) shouldBe null
    }

    @Test
    fun `the altitude carries the counts it was given`() {
        NativeMapViewModel.capAltitude(
            capped = true,
            cutoffFeet = 24_125.0,
            shownCount = 2000,
            totalMatching = 5000,
        ) shouldBe CapAltitude(aboveFeet = 24_000, shownCount = 2000, totalMatching = 5000)
    }

    @Test
    fun `an unknown total stays unknown`() {
        NativeMapViewModel.capAltitude(
            capped = true,
            cutoffFeet = 24_125.0,
            shownCount = 2000,
            totalMatching = null,
        ) shouldBe CapAltitude(aboveFeet = 24_000, shownCount = 2000, totalMatching = null)
    }
}
