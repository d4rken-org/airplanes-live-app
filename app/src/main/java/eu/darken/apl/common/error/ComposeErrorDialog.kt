package eu.darken.apl.common.error

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun ErrorDialog(
    throwable: Throwable,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val localizedError = throwable.localized(context)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = localizedError.label.get(context),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column {
                SelectionContainer {
                    Text(
                        text = localizedError.description.get(context),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = context.getString(android.R.string.ok))
            }
        }
    )
}
