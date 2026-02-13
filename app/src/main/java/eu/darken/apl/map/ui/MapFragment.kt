package eu.darken.apl.map.ui

import android.Manifest
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.apl.R
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.uix.Fragment3
import eu.darken.apl.common.viewbinding.viewBinding
import eu.darken.apl.databinding.MapFragmentBinding
import eu.darken.apl.main.ui.MainActivity
import eu.darken.apl.map.core.MapHandler
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject


@AndroidEntryPoint
class MapFragment : Fragment3(R.layout.map_fragment) {

    override val vm: MapViewModel by viewModels()
    override val ui: MapFragmentBinding by viewBinding()

    @Inject lateinit var mapHandlerFactory: MapHandler.Factory

    private var isFullscreen = false

    private lateinit var locationPermissionLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        locationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            log(TAG) { "locationPermissionLauncher: $isGranted" }
        }
        super.onCreate(savedInstanceState)

        // Restore fullscreen state if it was saved
        savedInstanceState?.let {
            isFullscreen = it.getBoolean(KEY_FULLSCREEN, false)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Save fullscreen state
        outState.putBoolean(KEY_FULLSCREEN, isFullscreen)
    }

    private fun getBottomNavigationView(): BottomNavigationView? {
        val parentFragment = parentFragment?.parentFragment
        return parentFragment?.view?.findViewById(R.id.bottom_navigation)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        ui.fullscreenButton.setOnClickListener { toggleFullscreen() }

        if (isFullscreen) {
            ui.toolbar.visibility = View.GONE
            val params = ui.webview.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            params.topToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            ui.webview.layoutParams = params
            ui.fullscreenButton.icon = ContextCompat.getDrawable(
                requireContext(),
                R.drawable.ic_fullscreen_exit_24
            )
            getBottomNavigationView()?.visibility = View.GONE
            setImmersiveMode(true)
        } else {
            ui.fullscreenButton.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_fullscreen_24)
        }

        ui.toolbar.apply {
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.action_settings -> {
                        (requireActivity() as MainActivity).goToSettings()
                        true
                    }

                    R.id.action_reload_map -> {
                        ui.webview.reload()
                        true
                    }

                    R.id.action_reset_map -> {
                        vm.reset()
                        true
                    }


                    else -> false
                }
            }
        }

        val mapHandler = mapHandlerFactory.create(ui.webview).apply {
            events
                .onEach { event ->
                    when (event) {
                        MapHandler.Event.HomePressed -> vm.checkLocationPermission()
                        is MapHandler.Event.OpenUrl -> vm.onOpenUrl(event.url)
                        is MapHandler.Event.OptionsChanged -> vm.onOptionsUpdated(event.options)
                        is MapHandler.Event.ShowInSearch -> vm.showInSearch(event.hex)
                        is MapHandler.Event.AddWatch -> vm.addWatch(event.hex)
                    }
                }
                .launchIn(viewLifecycleOwner.lifecycleScope)
        }

        vm.state.observeWith(ui) { state ->
            mapHandler.loadMap(state.options)
            mapHandler.updateWatches(state.alerts)
        }

        vm.routeDisplay
            .onEach { display ->
                when (display) {
                    is MapViewModel.RouteDisplay.Loading -> mapHandler.showFlightRouteLoading(display.hex)
                    is MapViewModel.RouteDisplay.Result -> mapHandler.showFlightRoute(display.hex, display.route)
                    null -> mapHandler.clearFlightRoute()
                }
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)

        vm.events.observe { event ->
            when (event) {
                MapEvents.RequestLocationPermission -> {
                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }

                MapEvents.HomeMap -> {
                    mapHandler.clickHome()
                }

                is MapEvents.WatchAdded -> {
                    val ac = event.watch.tracked.firstOrNull()
                    val text = getString(R.string.watch_item_x_added, ac?.registration ?: ac?.hex)
                    Snackbar.make(requireView(), text, Snackbar.LENGTH_SHORT).show()
                }
            }
        }

        super.onViewCreated(view, savedInstanceState)
    }

    override fun onResume() {
        ui.webview.onResume()
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
        ui.webview.onPause()
    }

    private fun setImmersiveMode(enable: Boolean) {
        val window = requireActivity().window
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(!enable)
            window.insetsController?.let {
                if (enable) {
                    it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                    it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } else {
                    it.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                }
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = if (enable) {
                (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN)
            } else {
                (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
            }
        }
    }


    private fun toggleFullscreen() {
        isFullscreen = !isFullscreen

        if (isFullscreen) {
            ui.toolbar.visibility = View.GONE
            val params = ui.webview.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            params.topToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            ui.webview.layoutParams = params
            ui.fullscreenButton.icon = ContextCompat.getDrawable(
                requireContext(),
                R.drawable.ic_fullscreen_exit_24
            )
            getBottomNavigationView()?.visibility = View.GONE
            setImmersiveMode(true)
        } else {
            ui.toolbar.visibility = View.VISIBLE
            val params = ui.webview.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            params.topToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
            params.topToBottom = R.id.toolbar
            ui.webview.layoutParams = params
            ui.fullscreenButton.icon = ContextCompat.getDrawable(
                requireContext(),
                R.drawable.ic_fullscreen_24
            )
            getBottomNavigationView()?.visibility = View.VISIBLE
            setImmersiveMode(false)
        }
    }

    companion object {
        private val TAG = logTag("Map", "Fragment")
        private const val KEY_FULLSCREEN = "map_fullscreen_state"
    }
}