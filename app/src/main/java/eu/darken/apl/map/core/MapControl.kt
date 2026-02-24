package eu.darken.apl.map.core

import androidx.annotation.StringRes
import eu.darken.apl.R

enum class MapControl(
    val buttonId: String,
    @StringRes val labelRes: Int,
    val type: ControlType,
    val requiresSelection: Boolean = false,
) {
    MILITARY("U", R.string.map_control_military, ControlType.TOGGLE),
    ALL_TRACKS("T", R.string.map_control_all_tracks, ControlType.ACTION),
    LABELS("L", R.string.map_control_labels, ControlType.TOGGLE),
    LABEL_EXTENSIONS("O", R.string.map_control_label_extensions, ControlType.TOGGLE),
    TRACK_LABELS("K", R.string.map_control_track_labels, ControlType.TOGGLE),
    MULTI_SELECT("M", R.string.map_control_multiselect, ControlType.TOGGLE),
    PERSISTENCE("P", R.string.map_control_persistence, ControlType.TOGGLE),
    ISOLATE("I", R.string.map_control_isolate, ControlType.TOGGLE, requiresSelection = true),
    RANDOM("R", R.string.map_control_random, ControlType.ACTION),
    FOLLOW("F", R.string.map_control_follow, ControlType.TOGGLE, requiresSelection = true),
    ;

    enum class ControlType { TOGGLE, ACTION }

    companion object {
        fun fromButtonId(id: String): MapControl? = entries.find { it.buttonId == id }
    }
}
