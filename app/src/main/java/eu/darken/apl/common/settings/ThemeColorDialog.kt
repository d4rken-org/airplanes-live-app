package eu.darken.apl.common.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import eu.darken.apl.R
import eu.darken.apl.common.compose.Preview2
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.common.theming.ThemeColor
import eu.darken.apl.common.theming.ThemePalette
import eu.darken.apl.common.theming.ThemeStyle
import eu.darken.apl.main.core.ThemeState

private enum class ThemeColorDialogPage { SELECTOR, EDITOR }

/**
 * Nothing is written while the editor page is open. Its draft starts from [customSeed] and
 * [customPalette] each time Custom is picked and ignores later emissions of either.
 */
@Composable
fun ThemeColorDialog(
    selectedOption: ThemeColor,
    customSeed: Int?,
    customPalette: ThemePalette,
    style: ThemeStyle,
    isUpgraded: Boolean,
    onPresetPicked: (ThemeColor) -> Unit,
    onCustomApplied: (Int?, ThemePalette) -> Unit,
    onUpgrade: () -> Unit,
    onDismiss: () -> Unit,
) {
    var page by rememberSaveable { mutableStateOf(ThemeColorDialogPage.SELECTOR) }
    var draft by rememberSaveable(stateSaver = HsvSaver) {
        mutableStateOf((customSeed ?: ThemeState.DEFAULT_CUSTOM_SEED).toHsv())
    }
    var draftPalette by rememberSaveable(stateSaver = ThemePaletteSaver) { mutableStateOf(customPalette) }

    val customThemeSeed = remember(customSeed, customPalette) {
        ThemeState(customSeed = customSeed, customPalette = customPalette).customThemeSeed
    }

    val editorDismissButtons: @Composable () -> Unit = {
        Row {
            TextButton(
                onClick = {
                    draft = ThemeState.DEFAULT_CUSTOM_SEED.toHsv()
                    draftPalette = ThemePalette.TONAL_SPOT
                },
            ) {
                Text(stringResource(R.string.ui_theme_custom_default_action))
            }
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel_action))
            }
        }
    }

    // One AlertDialog call for both pages: two call sites would swap dialog windows on page change.
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = when (page) {
                    ThemeColorDialogPage.SELECTOR -> stringResource(R.string.ui_theme_color_setting_label)
                    ThemeColorDialogPage.EDITOR -> stringResource(R.string.ui_theme_custom_dialog_title)
                },
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            when (page) {
                ThemeColorDialogPage.SELECTOR -> ThemeColorSelectorContent(
                    selectedOption = selectedOption,
                    customSeed = customThemeSeed,
                    isUpgraded = isUpgraded,
                    onOptionSelected = { option ->
                        when {
                            option != ThemeColor.CUSTOM -> onPresetPicked(option)
                            !isUpgraded -> onUpgrade()
                            else -> {
                                draft = (customSeed ?: ThemeState.DEFAULT_CUSTOM_SEED).toHsv()
                                draftPalette = customPalette
                                page = ThemeColorDialogPage.EDITOR
                            }
                        }
                    },
                )

                ThemeColorDialogPage.EDITOR -> CustomThemeEditorContent(
                    picked = draft,
                    onPickedChange = { draft = it },
                    palette = draftPalette,
                    onPaletteChange = { draftPalette = it },
                    style = style,
                )
            }
        },
        confirmButton = {
            when (page) {
                ThemeColorDialogPage.SELECTOR -> TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_cancel_action))
                }

                ThemeColorDialogPage.EDITOR -> TextButton(onClick = { onCustomApplied(draft.toRgb(), draftPalette) }) {
                    Text(stringResource(R.string.ui_theme_custom_apply_action))
                }
            }
        },
        dismissButton = if (page == ThemeColorDialogPage.EDITOR) editorDismissButtons else null,
    )
}

@Preview2
@Composable
private fun ThemeColorDialogPreview() {
    PreviewWrapper {
        ThemeColorDialog(
            selectedOption = ThemeColor.BLUE,
            customSeed = null,
            customPalette = ThemePalette.TONAL_SPOT,
            style = ThemeStyle.DEFAULT,
            isUpgraded = false,
            onPresetPicked = {},
            onCustomApplied = { _, _ -> },
            onUpgrade = {},
            onDismiss = {},
        )
    }
}

@Preview2
@Composable
private fun ThemeColorDialogUpgradedPreview() {
    PreviewWrapper {
        ThemeColorDialog(
            selectedOption = ThemeColor.CUSTOM,
            customSeed = 0x8C1C13,
            customPalette = ThemePalette.TONAL_SPOT,
            style = ThemeStyle.DEFAULT,
            isUpgraded = true,
            onPresetPicked = {},
            onCustomApplied = { _, _ -> },
            onUpgrade = {},
            onDismiss = {},
        )
    }
}
