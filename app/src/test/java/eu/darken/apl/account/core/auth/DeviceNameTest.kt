package eu.darken.apl.account.core.auth

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class DeviceNameTest : BaseTest() {

    @Test
    fun `manufacturer prefixed to model`() {
        DeviceName.format("Google", "Pixel 8") shouldBe "Google Pixel 8"
    }

    @Test
    fun `model already starts with manufacturer is not duplicated`() {
        DeviceName.format("samsung", "Samsung Galaxy S21") shouldBe "Samsung Galaxy S21"
    }

    @Test
    fun `model casing is preserved`() {
        DeviceName.format("samsung", "SM-G991B") shouldBe "Samsung SM-G991B"
    }

    @Test
    fun `only model`() {
        DeviceName.format(null, "Pixel 8") shouldBe "Pixel 8"
    }

    @Test
    fun `only manufacturer is capitalized`() {
        DeviceName.format("oneplus", null) shouldBe "Oneplus"
    }

    @Test
    fun `whitespace is collapsed and trimmed`() {
        DeviceName.format("  Google ", "Pixel   8 ") shouldBe "Google Pixel 8"
    }

    @Test
    fun `blank or unknown yields null`() {
        DeviceName.format("", "").shouldBeNull()
        DeviceName.format("   ", null).shouldBeNull()
        DeviceName.format("unknown", "unknown").shouldBeNull()
        DeviceName.format(null, null).shouldBeNull()
    }

    @Test
    fun `result is truncated to 120 chars`() {
        val long = "x".repeat(200)
        DeviceName.format(null, long)!!.length shouldBe 120
    }

    @Test
    fun `ascii control chars are stripped`() {
        DeviceName.format("Goo\u0000gle", "Pixel\t\n8") shouldBe "Goo gle Pixel 8"
        DeviceName.format(null, "Pi\u0007xel") shouldBe "Pi xel"
    }

    @Test
    fun `unicode format and c1 control chars are stripped`() {
        // C1 control (U+0085), zero-width joiner (U+200D), bidi mark (U+200E)
        DeviceName.format(null, "Pixel\u0085\u200D\u200E8") shouldBe "Pixel 8"
    }

    @Test
    fun `emoji survives normalization`() {
        DeviceName.format(null, "Pixel \uD83D\uDE80 8") shouldBe "Pixel \uD83D\uDE80 8"
    }

    @Test
    fun `truncation never splits a surrogate pair`() {
        // 119 chars + a 2-unit emoji: a raw take(120) would keep only the high half.
        val model = "x".repeat(119) + "\uD83D\uDE80"
        val result = DeviceName.format(null, model)!!
        result shouldBe "x".repeat(119)
    }

    @Test
    fun `unpaired surrogate is stripped`() {
        DeviceName.format(null, "Pixel\uD83D 8") shouldBe "Pixel 8"
    }
}
