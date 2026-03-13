package eu.darken.apl.ar.core

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class ArDwellTrackerTest : BaseTest() {

    private fun createTracker(thresholdMs: Long = 3000L) = ArDwellTracker(thresholdMs)

    @Test
    fun `empty visible set returns empty`() {
        val tracker = createTracker()
        tracker.update(emptySet(), nowMs = 0L).shouldBeEmpty()
    }

    @Test
    fun `visible below threshold returns empty`() {
        val tracker = createTracker()
        tracker.update(setOf("AABBCC"), nowMs = 0L).shouldBeEmpty()
        tracker.update(setOf("AABBCC"), nowMs = 2999L).shouldBeEmpty()
    }

    @Test
    fun `visible at threshold returns hex`() {
        val tracker = createTracker()
        tracker.update(setOf("AABBCC"), nowMs = 0L)
        val result = tracker.update(setOf("AABBCC"), nowMs = 3000L)
        result shouldBe setOf("AABBCC")
    }

    @Test
    fun `departure clears state and re-entry resets timer`() {
        val tracker = createTracker()
        tracker.update(setOf("AABBCC"), nowMs = 0L)
        tracker.update(setOf("AABBCC"), nowMs = 2000L)
        // Aircraft departs
        tracker.update(emptySet(), nowMs = 2500L).shouldBeEmpty()
        // Re-enters
        tracker.update(setOf("AABBCC"), nowMs = 3000L).shouldBeEmpty()
        // Needs full threshold from re-entry
        tracker.update(setOf("AABBCC"), nowMs = 5999L).shouldBeEmpty()
        tracker.update(setOf("AABBCC"), nowMs = 6000L) shouldBe setOf("AABBCC")
    }

    @Test
    fun `multiple hexes tracked independently`() {
        val tracker = createTracker()
        tracker.update(setOf("AAA", "BBB"), nowMs = 0L)
        tracker.update(setOf("AAA", "BBB"), nowMs = 2000L).shouldBeEmpty()
        // Only AAA continues, BBB departs
        tracker.update(setOf("AAA"), nowMs = 3000L) shouldBe setOf("AAA")
        // BBB re-enters with fresh timer
        tracker.update(setOf("AAA", "BBB"), nowMs = 4000L) shouldBe setOf("AAA")
        tracker.update(setOf("AAA", "BBB"), nowMs = 7000L) shouldBe setOf("AAA", "BBB")
    }
}
