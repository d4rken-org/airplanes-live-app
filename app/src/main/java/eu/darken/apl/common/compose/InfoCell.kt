package eu.darken.apl.common.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun InfoCell(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    isAlert: Boolean = false,
) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = if (isAlert) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
