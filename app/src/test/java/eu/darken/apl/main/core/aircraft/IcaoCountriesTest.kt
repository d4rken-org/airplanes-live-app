package eu.darken.apl.main.core.aircraft

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.util.Locale

class IcaoCountriesTest : BaseTest() {

    @Test
    fun `range edges belong to their state`() {
        IcaoCountries.countryCode("3C0000") shouldBe "de"
        IcaoCountries.countryCode("3FFFFF") shouldBe "de"
        IcaoCountries.countryCode("A00000") shouldBe "us"
        IcaoCountries.countryCode("AFFFFF") shouldBe "us"
        IcaoCountries.countryCode("a1b2c3") shouldBe "us"
    }

    @Test
    fun `territories inside a state's range win over the state`() {
        IcaoCountries.countryCode("400000") shouldBe "bm"
        IcaoCountries.countryCode("424B00") shouldBe "im"
        IcaoCountries.countryCode("4001C0") shouldBe "ky"
        IcaoCountries.countryCode("400200") shouldBe "gb"
        IcaoCountries.countryCode("43FFFF") shouldBe "gb"
    }

    @Test
    fun `addresses without a state have no country`() {
        IcaoCountries.countryCode("000000").shouldBeNull()
        IcaoCountries.countryCode("~2a3b4c").shouldBeNull()
        IcaoCountries.countryCode("F00001").shouldBeNull()
    }

    @Test
    fun `country names follow the locale`() {
        IcaoCountries.countryName("3C65A3", Locale.ENGLISH) shouldBe "Germany"
        IcaoCountries.countryName("3C65A3", Locale.GERMAN) shouldBe "Deutschland"
        IcaoCountries.countryName("000000", Locale.ENGLISH).shouldBeNull()
    }
}
