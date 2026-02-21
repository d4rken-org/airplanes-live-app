package eu.darken.apl.common.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun InfoCell(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    isAlert: Boolean = false,
    monoValue: Boolean = false,
) {
    Column(modifier = modifier) {
        val valueStyle = MaterialTheme.typography.bodySmall.copy(
            fontFamily = if (monoValue) FontFamily.Monospace else null
        )
        if (isAlert) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ) {
                Text(
                    text = value,
                    style = valueStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        } else {
            Text(
                text = value,
                style = valueStyle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Preview2
@Composable
private fun InfoCellPreview() {
    PreviewWrapper { InfoCell(value = "35000 ft", label = "Altitude") }
}

@Preview2
@Composable
private fun InfoCellAlertPreview() {
    PreviewWrapper { InfoCell(value = "7700", label = "Squawk", isAlert = true) }
}

@Preview2
@Composable
private fun InfoCellMonoPreview() {
    PreviewWrapper { InfoCell(value = "#4CA2B1", label = "Hex", monoValue = true) }
}
