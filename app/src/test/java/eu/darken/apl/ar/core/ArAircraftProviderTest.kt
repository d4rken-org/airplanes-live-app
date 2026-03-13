package eu.darken.apl.ar.core

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class ArAircraftProviderTest : BaseTest() {

    @Test
    fun `parseAltitudeFt handles normal altitude`() {
        ArAircraftProvider.parseAltitudeFt("35000") shouldBe 35000
    }

    @Test
    fun `parseAltitudeFt handles commas`() {
        ArAircraftProvider.parseAltitudeFt("35,000") shouldBe 35000
    }

    @Test
    fun `parseAltitudeFt handles ground`() {
        ArAircraftProvider.parseAltitudeFt("ground") shouldBe 0
    }

    @Test
    fun `parseAltitudeFt handles Ground with capital`() {
        ArAircraftProvider.parseAltitudeFt("Ground") shouldBe 0
    }

    @Test
    fun `parseAltitudeFt handles null`() {
        ArAircraftProvider.parseAltitudeFt(null).shouldBeNull()
    }

    @Test
    fun `parseAltitudeFt handles empty string`() {
        ArAircraftProvider.parseAltitudeFt("").shouldBeNull()
    }

    @Test
    fun `parseAltitudeFt handles whitespace`() {
        ArAircraftProvider.parseAltitudeFt("  35000  ") shouldBe 35000
    }

    @Test
    fun `parseAltitudeFt handles non-numeric string`() {
        ArAircraftProvider.parseAltitudeFt("abc").shouldBeNull()
    }

    @Test
    fun `parseAltitudeFt handles low altitude`() {
        ArAircraftProvider.parseAltitudeFt("500") shouldBe 500
    }

    @Test
    fun `parseAltitudeFt handles zero`() {
        ArAircraftProvider.parseAltitudeFt("0") shouldBe 0
    }
}
