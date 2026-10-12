package eu.darken.apl.map.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.Label
import androidx.compose.material.icons.automirrored.twotone.OpenInNew
import androidx.compose.material.icons.twotone.FilterAlt
import androidx.compose.material.icons.twotone.LibraryAddCheck
import androidx.compose.material.icons.twotone.PushPin
import androidx.compose.material.icons.twotone.RestartAlt
import androidx.compose.material.icons.twotone.Shield
import androidx.compose.material.icons.twotone.Timeline
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.apl.R
import eu.darken.apl.common.settings.SettingsSwitchItem

/** What the map shows and how taps act, as a short list of switches. */
@Composable
internal fun MapOptionsSheet(
    toggles: NativeMapViewModel.Toggles,
    canIsolate: Boolean,
    onToggleLabels: () -> Unit,
    onToggleAllTracks: () -> Unit,
    onToggleMilitaryHighlight: () -> Unit,
    onToggleMilitaryOnly: () -> Unit,
    onToggleIsolate: () -> Unit,
    onToggleMultiSelect: () -> Unit,
    onOpenWebsite: () -> Unit,
    onResetMap: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
        ) {
            Section(R.string.map_options_section_display)
            Option(Icons.AutoMirrored.TwoTone.Label, R.string.map_control_labels, R.string.map_control_labels_summary, toggles.labels, onToggle = onToggleLabels)
            Option(Icons.TwoTone.Timeline, R.string.map_control_all_tracks, R.string.map_control_all_tracks_summary, toggles.allTracks, onToggle = onToggleAllTracks)
            Option(
                Icons.TwoTone.Shield,
                R.string.map_control_military_highlight,
                R.string.map_control_military_highlight_summary,
                toggles.militaryHighlight,
                onToggle = onToggleMilitaryHighlight,
            )

            Section(R.string.map_options_section_filter)
            Option(Icons.TwoTone.FilterAlt, R.string.map_control_military, R.string.map_control_military_summary, toggles.militaryOnly, onToggle = onToggleMilitaryOnly)
            Option(
                Icons.TwoTone.PushPin,
                R.string.map_control_isolate,
                if (canIsolate) R.string.map_control_isolate_summary else R.string.map_control_isolate_unavailable_summary,
                toggles.isolate && canIsolate,
                enabled = canIsolate,
                onToggle = onToggleIsolate,
            )

            Section(R.string.map_options_section_selection)
            Option(
                Icons.TwoTone.LibraryAddCheck,
                R.string.map_control_multiselect,
                R.string.map_control_multiselect_summary,
                toggles.multiSelect,
                onToggle = onToggleMultiSelect,
            )

            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                FooterAction(Icons.AutoMirrored.TwoTone.OpenInNew, R.string.map_open_website_action, onOpenWebsite)
                FooterAction(Icons.TwoTone.RestartAlt, R.string.map_reset_action, onResetMap)
            }
        }
    }
}

@Composable
private fun Section(@StringRes titleRes: Int) {
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun Option(
    icon: ImageVector,
    @StringRes titleRes: Int,
    @StringRes summaryRes: Int,
    checked: Boolean,
    enabled: Boolean = true,
    onToggle: () -> Unit,
) {
    SettingsSwitchItem(
        title = stringResource(titleRes),
        summary = stringResource(summaryRes),
        checked = checked,
        icon = icon,
        enabled = enabled,
        onCheckedChange = { onToggle() },
    )
}

@Composable
private fun FooterAction(icon: ImageVector, @StringRes labelRes: Int, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(icon, contentDescription = null, modifier = Modifier.padding(end = ButtonDefaults.IconSpacing))
        Text(stringResource(labelRes))
    }
}
