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
import androidx.compose.material.icons.twotone.Block
import androidx.compose.material.icons.twotone.Flight
import androidx.compose.material.icons.twotone.Height
import androidx.compose.material.icons.twotone.Hexagon
import androidx.compose.material.icons.twotone.Layers
import androidx.compose.material.icons.twotone.LibraryAddCheck
import androidx.compose.material.icons.twotone.RestartAlt
import androidx.compose.material.icons.twotone.Security
import androidx.compose.material.icons.twotone.Shield
import androidx.compose.material.icons.twotone.Speed
import androidx.compose.material.icons.twotone.Tag
import androidx.compose.material.icons.twotone.Thunderstorm
import androidx.compose.material.icons.twotone.Visibility
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.apl.R
import eu.darken.apl.common.settings.SettingsSwitchItem
import eu.darken.apl.map.core.MapLabelField
import eu.darken.apl.map.ui.NativeMapViewModel.OverlayOption
import eu.darken.apl.server.api.OverlayDto

/** What the map shows and how taps act, as a short list of switches. */
@Composable
internal fun MapOptionsSheet(
    toggles: NativeMapViewModel.Toggles,
    overlays: List<OverlayOption>,
    onToggleLabels: () -> Unit,
    onToggleLabelField: (MapLabelField) -> Unit,
    onToggleAllTracks: () -> Unit,
    onToggleMilitaryHighlight: () -> Unit,
    onToggleWatchHighlight: () -> Unit,
    onToggleOverlay: (String) -> Unit,
    onUpgrade: () -> Unit,
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
            Option(Icons.TwoTone.Timeline, R.string.map_control_all_tracks, R.string.map_control_all_tracks_summary, toggles.allTracks, onToggle = onToggleAllTracks)
            Option(
                Icons.TwoTone.Shield,
                R.string.map_control_military_highlight,
                R.string.map_control_military_highlight_summary,
                toggles.militaryHighlight,
                onToggle = onToggleMilitaryHighlight,
            )
            Option(
                Icons.TwoTone.Visibility,
                R.string.map_control_watch_highlight,
                R.string.map_control_watch_highlight_summary,
                toggles.watchHighlight,
                onToggle = onToggleWatchHighlight,
            )

            Section(R.string.map_options_section_labels)
            Option(
                Icons.AutoMirrored.TwoTone.Label,
                R.string.map_control_labels_show,
                R.string.map_control_labels_show_summary,
                toggles.labels,
                modifier = Modifier.testTag(LABELS_SWITCH_TAG),
                onToggle = onToggleLabels,
            )
            MapLabelField.entries.forEach { field ->
                val fieldEnabled = toggles.labelFields.isEnabled(field)
                Option(
                    field.icon,
                    field.titleRes,
                    field.summaryRes,
                    fieldEnabled,
                    enabled = toggles.labels && (!fieldEnabled || toggles.labelFields.canDisable(field)),
                    modifier = Modifier.testTag(labelFieldTag(field)),
                    onToggle = { onToggleLabelField(field) },
                )
            }

            if (overlays.isNotEmpty()) {
                Section(R.string.map_options_section_overlays)
                overlays.forEach { OverlayRow(it, onToggleOverlay, onUpgrade) }
            }

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

internal const val LABELS_SWITCH_TAG = "map_labels_switch"

internal fun labelFieldTag(field: MapLabelField) = "map_label_field_${field.name}"

internal fun overlayTag(id: String) = "map_overlay_$id"

private val MapLabelField.icon: ImageVector
    get() = when (this) {
        MapLabelField.CALLSIGN -> Icons.TwoTone.Tag
        MapLabelField.TYPE -> Icons.TwoTone.Flight
        MapLabelField.ALTITUDE -> Icons.TwoTone.Height
        MapLabelField.SPEED -> Icons.TwoTone.Speed
    }

@get:StringRes
private val MapLabelField.titleRes: Int
    get() = when (this) {
        MapLabelField.CALLSIGN -> R.string.map_control_label_callsign
        MapLabelField.TYPE -> R.string.map_control_label_type
        MapLabelField.ALTITUDE -> R.string.map_control_label_altitude
        MapLabelField.SPEED -> R.string.map_control_label_speed
    }

@get:StringRes
private val MapLabelField.summaryRes: Int
    get() = when (this) {
        MapLabelField.CALLSIGN -> R.string.map_control_label_callsign_summary
        MapLabelField.TYPE -> R.string.map_control_label_type_summary
        MapLabelField.ALTITUDE -> R.string.map_control_label_altitude_summary
        MapLabelField.SPEED -> R.string.map_control_label_speed_summary
    }

private val OverlayOption.icon: ImageVector
    get() = when (id) {
        OverlayDto.ID_RADAR -> Icons.TwoTone.Thunderstorm
        OverlayDto.ID_AIRSPACE -> Icons.TwoTone.Hexagon
        OverlayDto.ID_RESTRICTIONS -> Icons.TwoTone.Block
        OverlayDto.ID_MILITARY -> Icons.TwoTone.Security
        else -> Icons.TwoTone.Layers
    }

@get:StringRes
private val OverlayOption.titleRes: Int?
    get() = when (id) {
        OverlayDto.ID_RADAR -> R.string.map_overlay_radar_title
        OverlayDto.ID_AIRSPACE -> R.string.map_overlay_airspace_title
        OverlayDto.ID_RESTRICTIONS -> R.string.map_overlay_restrictions_title
        OverlayDto.ID_MILITARY -> R.string.map_overlay_military_title
        else -> null
    }

@get:StringRes
private val OverlayOption.summaryRes: Int?
    get() = when (id) {
        OverlayDto.ID_RADAR -> R.string.map_overlay_radar_summary
        OverlayDto.ID_AIRSPACE -> R.string.map_overlay_airspace_summary
        OverlayDto.ID_RESTRICTIONS -> R.string.map_overlay_restrictions_summary
        OverlayDto.ID_MILITARY -> R.string.map_overlay_military_summary
        else -> null
    }

/** A locked overlay opens the upgrade instead of switching, and is shown off as it can't draw. */
@Composable
private fun OverlayRow(overlay: OverlayOption, onToggle: (String) -> Unit, onUpgrade: () -> Unit) {
    val description = overlay.summaryRes?.let { stringResource(it) }
    val dataNote = when {
        !overlay.available -> stringResource(R.string.map_overlay_no_data)
        overlay.coverage.isNotEmpty() -> stringResource(R.string.map_overlay_coverage, overlay.coverage.joinToString(", "))
        else -> null
    }
    SettingsSwitchItem(
        title = overlay.titleRes?.let { stringResource(it) } ?: overlay.title,
        summary = listOfNotNull(description, dataNote).joinToString("\n").ifEmpty { null },
        checked = overlay.checked && !overlay.locked,
        modifier = Modifier.testTag(overlayTag(overlay.id)),
        icon = overlay.icon,
        enabled = overlay.locked || overlay.available,
        requiresUpgrade = overlay.locked,
        onUpgrade = onUpgrade,
        onCheckedChange = { if (overlay.locked) onUpgrade() else onToggle(overlay.id) },
    )
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
    modifier: Modifier = Modifier,
    onToggle: () -> Unit,
) {
    SettingsSwitchItem(
        title = stringResource(titleRes),
        summary = stringResource(summaryRes),
        checked = checked,
        modifier = modifier,
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
