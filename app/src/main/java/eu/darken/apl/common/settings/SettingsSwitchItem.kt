package eu.darken.apl.common.settings

import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun SettingsSwitchItem(
    title: String,
    summary: String? = null,
    checked: Boolean,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    requiresUpgrade: Boolean = false,
    onUpgrade: (() -> Unit)? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsBaseItem(
        title = title,
        summary = summary,
        modifier = modifier,
        icon = icon,
        enabled = enabled,
        requiresUpgrade = requiresUpgrade,
        onUpgrade = onUpgrade,
        onClick = { onCheckedChange(!checked) },
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        },
    )
}
