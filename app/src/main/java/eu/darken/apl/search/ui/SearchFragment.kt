package eu.darken.apl.search.ui

import android.Manifest
import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isGone
import androidx.fragment.app.viewModels
import com.google.android.material.snackbar.Snackbar
import androidx.recyclerview.selection.SelectionTracker
import com.google.android.material.button.MaterialButtonToggleGroup
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.apl.R
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.lists.differ.update
import eu.darken.apl.common.lists.installListSelection
import eu.darken.apl.common.lists.setupDefaults
import eu.darken.apl.common.uix.Fragment3
import eu.darken.apl.common.viewbinding.viewBinding
import eu.darken.apl.databinding.SearchFragmentBinding
import eu.darken.apl.main.ui.MainActivity
import retrofit2.HttpException


@AndroidEntryPoint
class SearchFragment : Fragment3(R.layout.search_fragment) {

    override val vm: SearchViewModel by viewModels()
    override val ui: SearchFragmentBinding by viewBinding()

    private var tracker: SelectionTracker<String>? = null

    private lateinit var locationPermissionLauncher: ActivityResultLauncher<String>
    override fun onCreate(savedInstanceState: Bundle?) {
        locationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            log(TAG) { "locationPermissionLauncher: $isGranted" }
            // Should refresh automatically
        }
        super.onCreate(savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ui.toolbar.apply {
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.action_settings -> {
                        (requireActivity() as MainActivity).goToSettings()
                        true
                    }

                    else -> false
                }
            }
        }

        ui.apply {
            searchInput.apply {
                setOnEditorActionListener { view, actionId, _ ->
                    when (actionId) {
                        EditorInfo.IME_ACTION_SEARCH -> {
                            vm.updateSearchText(view.text.toString())
                            val imm =
                                requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                            imm.hideSoftInputFromWindow(view.windowToken, 0)
                            true
                        }

                        else -> false
                    }
                }
            }
            searchLayout.setEndIconOnClickListener {
                searchInput.setText("")
                vm.updateSearchText("")
            }
            searchExtraAction.setOnClickListener { vm.searchPositionHome() }
        }

        val adapter = SearchAdapter()
        ui.list.setupDefaults(adapter, verticalDividers = true)

        tracker = installListSelection(
            adapter = adapter,
            cabMenuRes = R.menu.menu_search_cab,
            onSelected = { tracker: SelectionTracker<String>, item: MenuItem, selected: Collection<SearchAdapter.Item> ->
                if (selected.isEmpty()) return@installListSelection false

                when (item.itemId) {
                    R.id.menu_show_on_map_action -> {
                        vm.showOnMap(selected)
                        tracker.clearSelection()
                        true
                    }

                    else -> false
                }
            }
        )

        val modeListener = object : MaterialButtonToggleGroup.OnButtonCheckedListener {
            override fun onButtonChecked(view: MaterialButtonToggleGroup, checkedId: Int, isChecked: Boolean) {
                if (!isChecked) return
                when (checkedId) {
                    ui.searchOptionAll.id -> vm.updateMode(SearchViewModel.State.Mode.ALL)
                    ui.searchOptionHex.id -> vm.updateMode(SearchViewModel.State.Mode.HEX)
                    ui.searchOptionCallsign.id -> vm.updateMode(SearchViewModel.State.Mode.CALLSIGN)
                    ui.searchOptionRegistration.id -> vm.updateMode(SearchViewModel.State.Mode.REGISTRATION)
                    ui.searchOptionSquawk.id -> vm.updateMode(SearchViewModel.State.Mode.SQUAWK)
                    ui.searchOptionAirframe.id -> vm.updateMode(SearchViewModel.State.Mode.AIRFRAME)
                    ui.searchOptionMilitary.id -> vm.updateMode(SearchViewModel.State.Mode.INTERESTING)
                    ui.searchOptionLocation.id -> vm.updateMode(SearchViewModel.State.Mode.POSITION)
                }
            }

        }

        vm.state.observeWith(ui) { state ->
            adapter.update(state.items)

            searchInput.apply {
                if (text.toString() != state.input.raw) {
                    setText(state.input.raw)
                    setSelection(text!!.length)
                }
            }

            searchOptionContainer.removeOnButtonCheckedListener(modeListener)
            when (state.input.mode) {
                SearchViewModel.State.Mode.ALL -> {
                    searchOptionContainer.check(ui.searchOptionAll.id)
                    searchLayout.setHint(R.string.search_mode_all_hint)
                    searchInput.inputType = InputType.TYPE_CLASS_TEXT
                    searchLayout.isEnabled = true
                }

                SearchViewModel.State.Mode.HEX -> {
                    searchOptionContainer.check(ui.searchOptionHex.id)
                    searchLayout.setHint(R.string.search_mode_hex_hint)
                    searchInput.inputType = InputType.TYPE_CLASS_TEXT
                    searchLayout.isEnabled = true
                }

                SearchViewModel.State.Mode.CALLSIGN -> {
                    searchOptionContainer.check(ui.searchOptionCallsign.id)
                    searchLayout.setHint(R.string.search_mode_callsign_hint)
                    searchInput.inputType = InputType.TYPE_CLASS_TEXT
                    searchLayout.isEnabled = true
                }

                SearchViewModel.State.Mode.REGISTRATION -> {
                    searchOptionContainer.check(ui.searchOptionRegistration.id)
                    searchLayout.setHint(R.string.search_mode_registration_hint)
                    searchInput.inputType = InputType.TYPE_CLASS_TEXT
                    searchLayout.isEnabled = true
                }

                SearchViewModel.State.Mode.SQUAWK -> {
                    searchOptionContainer.check(ui.searchOptionSquawk.id)
                    searchLayout.setHint(R.string.search_mode_squawk_hint)
                    searchInput.inputType = InputType.TYPE_CLASS_TEXT
                    searchLayout.isEnabled = true
                }

                SearchViewModel.State.Mode.AIRFRAME -> {
                    searchOptionContainer.check(ui.searchOptionAirframe.id)
                    searchLayout.setHint(R.string.search_mode_airframe_hint)
                    searchInput.inputType = InputType.TYPE_CLASS_TEXT
                    searchLayout.isEnabled = true
                }

                SearchViewModel.State.Mode.INTERESTING -> {
                    searchOptionContainer.check(ui.searchOptionMilitary.id)
                    searchLayout.setHint(R.string.search_mode_military_hint)
                    searchInput.inputType = InputType.TYPE_CLASS_TEXT
                    searchLayout.isEnabled = true
                }

                SearchViewModel.State.Mode.POSITION -> {
                    searchOptionContainer.check(ui.searchOptionLocation.id)
                    searchLayout.setHint(R.string.search_mode_location_hint)
                    searchInput.inputType = InputType.TYPE_CLASS_TEXT
                    searchLayout.isEnabled = true
                }
            }
            searchExtraAction.isGone = state.input.mode != SearchViewModel.State.Mode.POSITION
            searchOptionContainer.addOnButtonCheckedListener(modeListener)
        }

        vm.events.observe { event ->
            when (event) {
                SearchEvents.RequestLocationPermission -> {
                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }

                is SearchEvents.SearchError -> {
                    val message = when {
                        event.error is HttpException && (event.error as HttpException).code() == 429 ->
                            getString(R.string.search_error_rate_limited)
                        event.error.message?.contains("rate limit", ignoreCase = true) == true ->
                            getString(R.string.search_error_rate_limited)
                        else ->
                            getString(R.string.search_error_generic, event.error.message ?: event.error.toString())
                    }
                    Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG).show()
                }
            }
        }

        super.onViewCreated(view, savedInstanceState)
    }

    override fun onDestroyView() {
        tracker?.clearSelection()
        super.onDestroyView()
    }

    companion object {
        private val TAG = logTag("Search", "Fragment")
    }
}
