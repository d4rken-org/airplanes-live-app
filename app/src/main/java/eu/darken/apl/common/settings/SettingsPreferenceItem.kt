package eu.darken.apl.common.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun SettingsPreferenceItem(
    title: String,
    summary: String? = null,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    painter: Painter? = null,
    onClick: () -> Unit,
) {
    SettingsBaseItem(
        title = title,
        summary = summary,
        modifier = modifier,
        icon = icon,
        painter = painter,
        onClick = onClick,
    )
}
