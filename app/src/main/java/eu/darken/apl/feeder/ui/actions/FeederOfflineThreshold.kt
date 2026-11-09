package eu.darken.apl.feeder.ui.actions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import eu.darken.apl.R
import eu.darken.apl.common.time.durationText
import eu.darken.apl.feeder.core.config.FeederConfig
import java.time.Duration

/** The "Notify when offline" row, tapping it opens [FeederOfflineThresholdDialog]. */
@Composable
fun FeederOfflineThresholdRow(
    threshold: Duration?,
    onSelect: (Duration?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { showDialog = true }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.feeder_monitor_offline_label),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = offlineThresholdText(threshold),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }

    if (showDialog) {
        FeederOfflineThresholdDialog(
            threshold = threshold,
            onSelect = {
                showDialog = false
                onSelect(it)
            },
            onDismiss = { showDialog = false },
        )
    }
}

/** A [threshold] that is none of the choices, e.g. from an old backup, selects nothing. */
@Composable
fun FeederOfflineThresholdDialog(
    threshold: Duration?,
    onSelect: (Duration?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feeder_monitor_offline_label)) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                Text(
                    text = stringResource(R.string.feeder_offline_threshold_description),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                (listOf(null) + FeederConfig.OFFLINE_THRESHOLD_CHOICES).forEach { choice ->
                    val selected = choice == threshold
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(choice) })
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Spacer(Modifier.width(16.dp))
                        Text(text = offlineThresholdText(choice), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel_action))
            }
        },
    )
}

@Composable
private fun offlineThresholdText(threshold: Duration?): String = when {
    threshold == null -> stringResource(R.string.feeder_offline_threshold_off)
    threshold in FeederConfig.OFFLINE_THRESHOLD_CHOICES -> {
        val hours = threshold.toHours().toInt()
        pluralStringResource(R.plurals.feeder_offline_threshold_hours_x, hours, hours)
    }

    else -> durationText(threshold, LocalConfiguration.current.locales[0])
}
