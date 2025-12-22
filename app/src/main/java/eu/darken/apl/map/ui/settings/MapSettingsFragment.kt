package eu.darken.apl.map.ui.settings

import androidx.annotation.Keep
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.apl.R
import eu.darken.apl.common.uix.PreferenceFragment2
import eu.darken.apl.map.core.MapSettings
import javax.inject.Inject

@Keep
@AndroidEntryPoint
class MapSettingsFragment : PreferenceFragment2() {

    private val vm: MapSettingsViewModel by viewModels()

    @Inject lateinit var mapSettings: MapSettings

    override val settings: MapSettings by lazy { mapSettings }
    override val preferenceFile: Int = R.xml.preferences_map
}
