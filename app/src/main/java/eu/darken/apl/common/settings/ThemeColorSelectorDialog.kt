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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.apl.common.theming.ThemeColor
import eu.darken.apl.common.theming.ThemeColorProvider
import eu.darken.apl.common.theming.ThemeSeed
import eu.darken.apl.common.theming.ThemeStyle

@Composable
internal fun ThemeColorSelectorContent(
    selectedOption: ThemeColor,
    customSeed: ThemeSeed,
    isUpgraded: Boolean,
    onOptionSelected: (ThemeColor) -> Unit,
) {
    Column {
        // Re-selecting a row still reports it: that is how the already-selected Custom row
        // doubles as "edit my custom theme".
        ThemeColor.entries.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOptionSelected(option) }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = option == selectedOption,
                    onClick = { onOptionSelected(option) },
                )
                Spacer(modifier = Modifier.width(16.dp))

                ColorPreviewIcon(themeColor = option, customSeed = customSeed)

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = stringResource(option.labelRes),
                    style = MaterialTheme.typography.bodyLarge,
                )

                if (option == ThemeColor.CUSTOM && !isUpgraded) {
                    Spacer(modifier = Modifier.width(8.dp))
                    UpgradeChip(onClick = { onOptionSelected(option) })
                }
            }
        }
    }
}

@Composable
private fun ColorPreviewIcon(themeColor: ThemeColor, customSeed: ThemeSeed) {
    val (lightColor, darkColor) = remember(themeColor, customSeed) {
        val seed = themeColor.preset ?: customSeed
        val dark = ThemeColorProvider.getColorScheme(seed, ThemeStyle.DEFAULT, dark = true)
        ThemeColorProvider.getColorScheme(seed, ThemeStyle.DEFAULT, dark = false).primary to
            if (seed.isAmoled) dark.background else dark.primary
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ColorSwatch(lightColor)
        ColorSwatch(darkColor)
    }
}

@Composable
private fun ColorSwatch(color: Color) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape = CircleShape,
            )
    )
}
