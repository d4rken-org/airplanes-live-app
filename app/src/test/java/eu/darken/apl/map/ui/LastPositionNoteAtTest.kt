package eu.darken.apl.map.ui

import eu.darken.apl.map.ui.NativeMapViewModel.Seen
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class LastPositionNoteAtTest : BaseTest() {

    private val seenAt = 1_700_000_000_000L

    private fun noteAt(
        inHistory: Boolean = false,
        selectedMissing: Boolean = true,
        seen: Seen? = Seen.At(seenAt),
        partialDetails: Boolean = true,
        unavailable: Boolean = false,
    ) = lastPositionNoteAt(
        inHistory = inHistory,
        selectedMissing = selectedMissing,
        seen = seen,
        partialDetails = partialDetails,
        unavailable = unavailable,
    )

    @Test
    fun `a missing aircraft with partial details says how old its position is`() {
        noteAt() shouldBe seenAt
    }

    @Test
    fun `a missing aircraft keeping its full details says how old its position is`() {
        noteAt(partialDetails = false) shouldBe seenAt
    }

    @Test
    fun `no note without a known position`() {
        noteAt(seen = Seen.Unknown) shouldBe null
        noteAt(seen = null) shouldBe null
    }

    @Test
    fun `no note while the aircraft is live`() {
        noteAt(seen = Seen.Live) shouldBe null
    }

    @Test
    fun `no note for an aircraft still drawn but fading`() {
        noteAt(selectedMissing = false) shouldBe null
    }

    @Test
    fun `no note in the history`() {
        noteAt(inHistory = true) shouldBe null
    }

    @Test
    fun `an unavailable map explains partial details instead`() {
        noteAt(unavailable = true) shouldBe null
    }

    @Test
    fun `full details say how old the position is while the map is unavailable`() {
        noteAt(partialDetails = false, unavailable = true) shouldBe seenAt
    }
}
