package eu.darken.apl.common

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class CountriesTest : BaseTest() {

    @Test
    fun `a two letter code becomes its flag`() {
        flagEmoji("DE") shouldBe "🇩🇪"
        flagEmoji("US") shouldBe "🇺🇸"
    }

    @Test
    fun `a lower case code becomes the same flag`() {
        flagEmoji("de") shouldBe flagEmoji("DE")
        flagEmoji("iT") shouldBe flagEmoji("IT")
    }

    @Test
    fun `anything that is not two ASCII letters has no flag`() {
        flagEmoji(null).shouldBeNull()
        flagEmoji("").shouldBeNull()
        flagEmoji("D").shouldBeNull()
        flagEmoji("DEU").shouldBeNull()
        flagEmoji("1A").shouldBeNull()
        flagEmoji("42").shouldBeNull()
        flagEmoji("ÄÖ").shouldBeNull()
        flagEmoji("D ").shouldBeNull()
    }
}
