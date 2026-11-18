package eu.darken.apl.feeder.ui.actions

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import eu.darken.apl.R
import eu.darken.apl.feeder.core.server.FeederProblemCategory

@Composable
fun FeederProblemNotificationsSection(
    categories: Set<FeederProblemCategory>,
    onToggle: (FeederProblemCategory, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        FeederProblemCategory.entries.forEach { category ->
            val checked = category in categories
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(value = checked, role = Role.Switch, onValueChange = { onToggle(category, it) })
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(category.titleRes))
                    Text(
                        text = stringResource(category.summaryRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(16.dp))
                Switch(checked = checked, onCheckedChange = null)
            }
        }
    }
}

@get:StringRes
private val FeederProblemCategory.titleRes: Int
    get() = when (this) {
        FeederProblemCategory.MESSAGE_RATE -> R.string.feeder_problem_notify_message_rate_label
        FeederProblemCategory.MLAT -> R.string.feeder_problem_notify_mlat_label
        FeederProblemCategory.CONNECTION -> R.string.feeder_problem_notify_connection_label
    }

@get:StringRes
private val FeederProblemCategory.summaryRes: Int
    get() = when (this) {
        FeederProblemCategory.MESSAGE_RATE -> R.string.feeder_problem_notify_message_rate_summary
        FeederProblemCategory.MLAT -> R.string.feeder_problem_notify_mlat_summary
        FeederProblemCategory.CONNECTION -> R.string.feeder_problem_notify_connection_summary
    }
