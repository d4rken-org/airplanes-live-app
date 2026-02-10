package eu.darken.apl.watch.ui.details

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.apl.R
import eu.darken.apl.common.WebpageTool
import eu.darken.apl.common.setChecked2
import eu.darken.apl.common.uix.BottomSheetDialogFragment2
import eu.darken.apl.databinding.WatchDetailsFragmentBinding
import eu.darken.apl.watch.core.types.AircraftWatch
import eu.darken.apl.watch.core.types.FlightWatch
import eu.darken.apl.watch.core.types.SquawkWatch
import javax.inject.Inject

@AndroidEntryPoint
class WatchDetailsFragment : BottomSheetDialogFragment2() {
    @Inject lateinit var webpageTool: WebpageTool

    override val vm: WatchDetailsViewModel by viewModels()
    override lateinit var ui: WatchDetailsFragmentBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        ui = WatchDetailsFragmentBinding.inflate(inflater, container, false)
        return ui.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ui.noteInput.addTextChangedListener {
            vm.updateNote(it.toString())
        }

        ui.enableNotificationsToggle.setOnCheckedChangeListener { v, checked -> vm.enableNotifications(checked) }
        ui.showMapAction.setOnClickListener { vm.showOnMap() }
        ui.removeFeederAction.setOnClickListener { vm.removeAlert() }
        ui.aircraftDetails.onThumbnailClicked = { webpageTool.open(it.link) }

        vm.state.observeWith(ui) { state ->
            when (state.status) {
                is AircraftWatch.Status -> {
                    icon.setImageResource(R.drawable.ic_hexagon_multiple_24)
                    primary.text = state.aircraft?.registration ?: "?"
                    primary2.text = "| #${state.status.hex}"
                    secondary.text = getString(R.string.watch_list_item_aircraft_subtitle)
                    aircraftDetails.isGone = state.aircraft == null
                    state.aircraft?.let {
                        aircraftDetails.setAircraft(it, state.distanceInMeter)
                        aircraftDetails.setRoute(state.route)
                    }
                }

                is FlightWatch.Status -> {
                    icon.setImageResource(R.drawable.ic_bullhorn_24)
                    primary.text = state.status.callsign
                    primary2.text = "| #${state.aircraft?.hex}"
                    secondary.text = getString(R.string.watch_list_item_flight_subtitle)
                    aircraftDetails.isGone = state.aircraft == null
                    state.aircraft?.let {
                        aircraftDetails.setAircraft(it, state.distanceInMeter)
                        aircraftDetails.setRoute(state.route)
                    }
                }

                is SquawkWatch.Status -> {
                    icon.setImageResource(R.drawable.ic_router_wireless_24)
                    primary.text = state.status.squawk
                    secondary.text = getString(R.string.watch_list_item_squawk_subtitle)
                    aircraftDetails.isGone = true
                }
            }


            if (noteInput.text?.isEmpty() == true) {
                noteInput.setText(state.status.note)
            }

            enableNotificationsToggle.setChecked2(state.status.watch.isNotificationEnabled, animate = false)
        }

        vm.events.observeWith(ui) { event ->
            when (event) {
                is WatchDetailsEvents.RemovalConfirmation -> MaterialAlertDialogBuilder(requireContext()).apply {
                    setTitle(R.string.watch_list_remove_confirmation_title)
                    setMessage(R.string.watch_list_remove_confirmation_message)
                    setPositiveButton(R.string.common_remove_action) { _, _ -> vm.removeAlert(confirmed = true) }
                    setNegativeButton(R.string.common_cancel_action) { _, _ -> }
                }.show()
            }
        }

        super.onViewCreated(view, savedInstanceState)
    }
}