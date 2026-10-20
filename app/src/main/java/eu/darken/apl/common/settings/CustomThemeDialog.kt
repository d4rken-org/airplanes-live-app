package eu.darken.apl.common.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import eu.darken.apl.R
import eu.darken.apl.common.compose.Preview2
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.common.theming.ThemeColorProvider
import eu.darken.apl.common.theming.ThemePalette
import eu.darken.apl.common.theming.ThemeSeed
import eu.darken.apl.common.theming.ThemeStyle
import eu.darken.apl.main.core.ThemeState
import java.util.Locale

internal val ThemePaletteSaver: Saver<ThemePalette, String> = Saver(
    save = { it.name },
    restore = { ThemePalette.valueOf(it) },
)

/** The roles the dialog's swatch row shows, in the order it shows them. */
internal fun customThemePreviewSwatches(
    seed: Color,
    palette: ThemePalette,
    style: ThemeStyle,
    dark: Boolean,
): List<Color> {
    val scheme = ThemeColorProvider.getColorScheme(
        seed = ThemeSeed(seed, palette),
        style = style,
        dark = dark,
    )
    return listOf(
        scheme.primary,
        scheme.secondary,
        scheme.tertiary,
        scheme.surface,
        scheme.surfaceContainerHigh,
    )
}

/**
 * Picks the seed the whole scheme is generated from, plus the palette style that decides how much
 * chroma the other roles take from it. The draft is kept as [Hsv], so the hue survives dragging
 * saturation or brightness to zero.
 */
@Composable
internal fun CustomThemeEditorContent(
    picked: Hsv,
    onPickedChange: (Hsv) -> Unit,
    palette: ThemePalette,
    onPaletteChange: (ThemePalette) -> Unit,
    style: ThemeStyle,
) {
    val seedColor = picked.toColor()
    val hex = "#%06X".format(Locale.ROOT, picked.toRgb())
    val hexDescription = stringResource(R.string.ui_theme_custom_value_description, hex)
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    val preview = remember(seedColor, palette, style, isDark) {
        customThemePreviewSwatches(
            seed = seedColor,
            palette = palette,
            style = style,
            dark = isDark,
        )
    }

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            preview.forEach { role ->
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(role)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(8.dp),
                        ),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.ui_theme_custom_seed_label),
            style = MaterialTheme.typography.titleSmall,
        )

        Spacer(Modifier.height(8.dp))

        SaturationBrightnessField(
            picked = picked,
            onPickedChange = onPickedChange,
        )

        Spacer(Modifier.height(8.dp))

        ChannelSlider(
            label = stringResource(R.string.ui_theme_custom_hue_label),
            value = picked.hue,
            valueRange = 0f..360f,
            onValueChange = { onPickedChange(picked.copy(hue = it)) },
        )
        ChannelSlider(
            label = stringResource(R.string.ui_theme_custom_saturation_label),
            value = picked.saturation,
            valueRange = 0f..1f,
            onValueChange = { onPickedChange(picked.copy(saturation = it)) },
        )
        ChannelSlider(
            label = stringResource(R.string.ui_theme_custom_brightness_label),
            value = picked.brightness,
            valueRange = 0f..1f,
            onValueChange = { onPickedChange(picked.copy(brightness = it)) },
        )

        Text(
            text = hex,
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = hexDescription
            },
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.ui_theme_custom_palette_label),
            style = MaterialTheme.typography.titleSmall,
        )

        ThemePalette.entries.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPaletteChange(option) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = option == palette,
                    onClick = { onPaletteChange(option) },
                )
                Text(
                    text = stringResource(option.labelRes),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Preview2
@Composable
private fun CustomThemeEditorPreview() {
    PreviewWrapper {
        CustomThemeEditorContent(
            picked = ThemeState.DEFAULT_CUSTOM_SEED.toHsv(),
            onPickedChange = {},
            palette = ThemePalette.TONAL_SPOT,
            onPaletteChange = {},
            style = ThemeStyle.DEFAULT,
        )
    }
}

@Preview2
@Composable
private fun CustomThemeEditorMonochromePreview() {
    PreviewWrapper {
        CustomThemeEditorContent(
            picked = 0x1565C0.toHsv(),
            onPickedChange = {},
            palette = ThemePalette.MONOCHROME,
            onPaletteChange = {},
            style = ThemeStyle.HIGH_CONTRAST,
        )
    }
}

@Preview2
@Composable
private fun CustomThemeEditorVibrantPreview() {
    PreviewWrapper {
        CustomThemeEditorContent(
            picked = 0x8C1C13.toHsv(),
            onPickedChange = {},
            palette = ThemePalette.VIBRANT,
            onPaletteChange = {},
            style = ThemeStyle.DEFAULT,
        )
    }
}
