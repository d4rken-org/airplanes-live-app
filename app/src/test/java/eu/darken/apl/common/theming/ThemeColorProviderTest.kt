package eu.darken.apl.common.theming

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import eu.darken.apl.main.core.ThemeState
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import kotlin.math.pow

class ThemeColorProviderTest : BaseTest() {

    private fun Color.relativeLuminance(): Double {
        fun channel(value: Float): Double {
            val raw = value.toDouble()
            return if (raw <= 0.03928) raw / 12.92 else ((raw + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(red) + 0.7152 * channel(green) + 0.0722 * channel(blue)
    }

    private fun contrastRatio(one: Color, other: Color): Double {
        val first = one.relativeLuminance()
        val second = other.relativeLuminance()
        return (maxOf(first, second) + 0.05) / (minOf(first, second) + 0.05)
    }

    private fun ColorScheme.roles(): List<Color> = listOf(
        primary, onPrimary, primaryContainer, onPrimaryContainer,
        secondary, onSecondary, secondaryContainer, onSecondaryContainer,
        tertiary, onTertiary, tertiaryContainer, onTertiaryContainer,
        error, onError, errorContainer, onErrorContainer,
        background, onBackground, surface, onSurface,
        surfaceVariant, onSurfaceVariant, outline, outlineVariant,
        inverseSurface, inverseOnSurface, inversePrimary,
        surfaceDim, surfaceBright,
        surfaceContainerLowest, surfaceContainerLow, surfaceContainer,
        surfaceContainerHigh, surfaceContainerHighest,
    )

    private fun ColorScheme.shouldBeOpaque() {
        primary.alpha shouldBe 1f
        surface.alpha shouldBe 1f
        background.alpha shouldBe 1f
    }

    @Test
    fun `every theme color, style and mode resolves`() {
        ThemeColor.entries.forEach { color ->
            val seed = ThemeState(color = color).themeSeed
            ThemeStyle.entries.forEach { style ->
                listOf(true, false).forEach { dark ->
                    withClue("$color $style dark=$dark") {
                        ThemeColorProvider.getColorScheme(seed, style, dark).shouldBeOpaque()
                    }
                }
            }
        }
    }

    @Test
    fun `custom resolves to the stored seed and palette`() {
        val state = ThemeState(color = ThemeColor.CUSTOM, customSeed = 0xE53935, customPalette = ThemePalette.VIBRANT)

        state.themeSeed shouldBe ThemeSeed(Color(0xFFE53935), ThemePalette.VIBRANT)
        listOf(true, false).forEach { dark ->
            withClue("dark=$dark") {
                ThemeColorProvider.getColorScheme(state.themeSeed, ThemeStyle.DEFAULT, dark).shouldBeOpaque()
            }
        }
    }

    @Test
    fun `AMOLED dark uses pure black background and surface`() {
        val seed = ThemeColor.AMOLED.preset!!
        ThemeStyle.entries.forEach { style ->
            val scheme = ThemeColorProvider.getColorScheme(seed, style, dark = true)
            withClue("$style") {
                scheme.background shouldBe Color.Black
                scheme.surface shouldBe Color.Black
            }
        }
    }

    @Test
    fun `high contrast separates primary from surface further than the default does`() {
        ThemeColor.entries.forEach { color ->
            val seed = ThemeState(color = color).themeSeed
            listOf(true, false).forEach { dark ->
                val default = ThemeColorProvider.getColorScheme(seed, ThemeStyle.DEFAULT, dark)
                val high = ThemeColorProvider.getColorScheme(seed, ThemeStyle.HIGH_CONTRAST, dark)

                val defaultRatio = contrastRatio(default.primary, default.surface)
                val highRatio = contrastRatio(high.primary, high.surface)

                withClue("$color dark=$dark: default $defaultRatio vs high $highRatio") {
                    (highRatio > defaultRatio) shouldBe true
                }
            }
        }
    }

    @Test
    fun `every palette resolves for edge and typical seeds in both modes`() {
        val seeds = listOf(0x000000, 0xFFFFFF, 0x2D638E, 0xE53935)
        ThemePalette.entries.forEach { palette ->
            seeds.forEach { rgb ->
                val seed = ThemeState(customSeed = rgb, customPalette = palette).customThemeSeed
                listOf(true, false).forEach { dark ->
                    withClue("$palette seed=${rgb.toString(16)} dark=$dark") {
                        ThemeColorProvider.getColorScheme(seed, ThemeStyle.DEFAULT, dark).shouldBeOpaque()
                    }
                }
            }
        }
    }

    @Test
    fun `each palette maps to its generator style`() {
        val expected = mapOf(
            ThemePalette.TONAL_SPOT to PaletteStyle.TonalSpot,
            ThemePalette.NEUTRAL to PaletteStyle.Neutral,
            ThemePalette.VIBRANT to PaletteStyle.Vibrant,
            ThemePalette.EXPRESSIVE to PaletteStyle.Expressive,
            ThemePalette.RAINBOW to PaletteStyle.Rainbow,
            ThemePalette.FRUIT_SALAD to PaletteStyle.FruitSalad,
            ThemePalette.MONOCHROME to PaletteStyle.Monochrome,
            ThemePalette.FIDELITY to PaletteStyle.Fidelity,
            ThemePalette.CONTENT to PaletteStyle.Content,
        )

        expected.keys shouldBe ThemePalette.entries.toSet()
        ThemePalette.entries.forEach { palette ->
            withClue("$palette") { palette.style shouldBe expected[palette] }
        }
    }

    @Test
    fun `the same seed generates the same scheme every time`() {
        val seed = ThemeSeed(Color(0xFF2D638E), ThemePalette.VIBRANT)

        listOf(true, false).forEach { dark ->
            val first = ThemeColorProvider.getColorScheme(seed, ThemeStyle.DEFAULT, dark)
            val second = ThemeColorProvider.getColorScheme(seed, ThemeStyle.DEFAULT, dark)

            withClue("dark=$dark") { first.roles() shouldBe second.roles() }
        }
    }

    @Test
    fun `a stored seed without alpha resolves to an opaque color`() {
        val stored = ThemeState(customSeed = 0x2D638E)

        stored.customThemeSeed.seed.alpha shouldBe 1f
        stored.customThemeSeed.seed shouldBe Color(0xFF2D638E)
    }

    @Test
    fun `an unset custom seed falls back to the default seed`() {
        ThemeState().customThemeSeed.seed shouldBe Color(ThemeState.DEFAULT_CUSTOM_SEED or 0xFF000000.toInt())
    }
}
