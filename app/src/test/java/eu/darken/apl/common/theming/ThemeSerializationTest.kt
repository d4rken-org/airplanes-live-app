package eu.darken.apl.common.theming

import eu.darken.apl.common.serialization.SerializationModule
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class ThemeSerializationTest : BaseTest() {

    private val json = SerializationModule().json()

    @Test
    fun `theme colors use their stored names`() {
        val stored = mapOf(
            ThemeColor.BLUE to "\"BLUE\"",
            ThemeColor.AMOLED to "\"AMOLED\"",
        )

        stored.keys shouldBe ThemeColor.entries.toSet()
        stored.forEach { (color, raw) ->
            json.encodeToString(ThemeColor.serializer(), color) shouldBe raw
            json.decodeFromString(ThemeColor.serializer(), raw) shouldBe color
        }
    }

    @Test
    fun `theme palettes use their stored names`() {
        val stored = mapOf(
            ThemePalette.TONAL_SPOT to "\"TONAL_SPOT\"",
            ThemePalette.NEUTRAL to "\"NEUTRAL\"",
            ThemePalette.VIBRANT to "\"VIBRANT\"",
            ThemePalette.EXPRESSIVE to "\"EXPRESSIVE\"",
            ThemePalette.RAINBOW to "\"RAINBOW\"",
            ThemePalette.FRUIT_SALAD to "\"FRUIT_SALAD\"",
            ThemePalette.MONOCHROME to "\"MONOCHROME\"",
            ThemePalette.FIDELITY to "\"FIDELITY\"",
            ThemePalette.CONTENT to "\"CONTENT\"",
        )

        stored.keys shouldBe ThemePalette.entries.toSet()
        stored.forEach { (palette, raw) ->
            withClue("$palette") {
                json.encodeToString(ThemePalette.serializer(), palette) shouldBe raw
                json.decodeFromString(ThemePalette.serializer(), raw) shouldBe palette
            }
        }
    }

    @Test
    fun `theme styles use their stored names`() {
        val stored = mapOf(
            ThemeStyle.DEFAULT to "\"DEFAULT\"",
            ThemeStyle.MATERIAL_YOU to "\"MATERIAL_YOU\"",
            ThemeStyle.MEDIUM_CONTRAST to "\"MEDIUM_CONTRAST\"",
            ThemeStyle.HIGH_CONTRAST to "\"HIGH_CONTRAST\"",
        )

        stored.keys shouldBe ThemeStyle.entries.toSet()
        stored.forEach { (style, raw) ->
            json.encodeToString(ThemeStyle.serializer(), style) shouldBe raw
            json.decodeFromString(ThemeStyle.serializer(), raw) shouldBe style
        }
    }
}
