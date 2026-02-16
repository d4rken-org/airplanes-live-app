package eu.darken.apl.common.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun SettingsPreferenceItem(
    title: String,
    summary: String? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    SettingsBaseItem(
        title = title,
        summary = summary,
        modifier = modifier,
        onClick = onClick,
    )
}
