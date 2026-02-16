package eu.darken.apl.main.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.apl.common.compose.LocalIsInternetAvailable
import eu.darken.apl.common.debug.logging.Logging.Priority.ERROR
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.debug.recorder.core.RecorderModule
import eu.darken.apl.common.error.ErrorEventHandler
import eu.darken.apl.common.navigation.LocalNavigationController
import eu.darken.apl.common.navigation.NavigationController
import eu.darken.apl.common.navigation.NavigationEntry
import eu.darken.apl.common.navigation.NavigationEventHandler
import eu.darken.apl.common.network.NetworkStateProvider
import eu.darken.apl.common.theming.AplTheme
import eu.darken.apl.common.theming.Theming
import eu.darken.apl.common.uix.Activity2
import eu.darken.apl.feeder.core.monitor.FeederMonitorNotifications
import eu.darken.apl.feeder.ui.add.NewFeederQR
import eu.darken.apl.watch.core.alerts.WatchAlertNotifications
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : Activity2() {

    private val vm: MainActivityVM by viewModels()

    @Inject lateinit var theming: Theming
    @Inject lateinit var recorderModule: RecorderModule
    @Inject lateinit var feederMonitorNotifications: FeederMonitorNotifications
    @Inject lateinit var navController: NavigationController
    @Inject lateinit var navigationEntries: Set<@JvmSuppressWildcards NavigationEntry>
    @Inject lateinit var networkStateProvider: NetworkStateProvider

    private var showSplashScreen = true
    private var pendingIntent: Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val splashScreen = installSplashScreen()
        theming.notifySplashScreenDone(this)
        splashScreen.setKeepOnScreenCondition { showSplashScreen && savedInstanceState == null }

        enableEdgeToEdge()

        feederMonitorNotifications.clearOfflineNotifications()
        pendingIntent = intent
        vm.onGo()

        setContent {
            CompositionLocalProvider(
                LocalNavigationEventDispatcherOwner provides this@MainActivity,
            ) {
                AplTheme {
                    val backStack = rememberNavBackStack(DestinationMain)
                    val isInternetAvailable by networkStateProvider.networkState
                        .map { it.isInternetAvailable }
                        .collectAsState(initial = true)

                    LaunchedEffect(Unit) {
                        navController.setup(backStack)
                        showSplashScreen = false
                        pendingIntent?.let { handleIntent(it) }
                        pendingIntent = null
                    }

                    CompositionLocalProvider(
                        LocalNavigationController provides navController,
                        LocalIsInternetAvailable provides isInternetAvailable,
                    ) {
                        NavigationEventHandler(vm)
                        ErrorEventHandler(vm)

                        NavDisplay(
                            backStack = backStack,
                            entryDecorators = listOf(
                                rememberSaveableStateHolderNavEntryDecorator(),
                                rememberViewModelStoreNavEntryDecorator(),
                            ),
                            entryProvider = entryProvider {
                                navigationEntries.forEach { navEntry ->
                                    with(navEntry) { setup() }
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        log(TAG) { "Handling intent $intent" }
        when (intent.action) {
            Intent.ACTION_MAIN -> {
                // NOOP
            }

            Intent.ACTION_VIEW -> {
                val data = intent.data
                if (data != null && NewFeederQR.isValid(data.toString())) {
                    log(TAG) { "Received feeder QR code: $data" }
                    navController.goTo(
                        eu.darken.apl.feeder.ui.DestinationAddFeeder(qrData = data.toString())
                    )
                } else {
                    log(TAG, WARN) { "Invalid or unsupported VIEW intent data: $data" }
                }
            }

            WatchAlertNotifications.ALERT_SHOW_ACTION -> {
                val watchId = intent.getStringExtra(WatchAlertNotifications.ARG_WATCHID)
                if (watchId == null) {
                    log(TAG, ERROR) { "watchId was null" }
                } else {
                    vm.showWatchAlert(watchId)
                }
            }

            else -> log(TAG, WARN) { "Unknown intent type: ${intent.action}" }
        }
    }

    companion object {
        private val TAG = logTag("Main", "Activity")
    }
}
