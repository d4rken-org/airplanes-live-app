package eu.darken.apl.main.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.apl.R
import eu.darken.apl.common.compose.LocalIsInternetAvailable
import eu.darken.apl.common.github.GithubApi
import eu.darken.apl.common.debug.logging.Logging.Priority.ERROR
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.debug.recorder.core.RecorderModule
import eu.darken.apl.common.error.ErrorEventHandler
import eu.darken.apl.common.navigation.BottomSheetSceneStrategy
import eu.darken.apl.common.navigation.LocalNavigationController
import eu.darken.apl.common.navigation.NavigationController
import eu.darken.apl.common.navigation.NavigationEntry
import eu.darken.apl.common.navigation.NavigationEventHandler
import eu.darken.apl.common.network.NetworkStateProvider
import eu.darken.apl.common.theming.AplTheme
import eu.darken.apl.main.core.ThemeState
import eu.darken.apl.common.uix.Activity2
import eu.darken.apl.feeder.core.monitor.FeederMonitorNotifications
import eu.darken.apl.feeder.ui.add.NewFeederQR
import eu.darken.apl.watch.core.alerts.WatchAlertNotifications
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : Activity2() {

    private val vm: MainActivityVM by viewModels()

    @Inject lateinit var recorderModule: RecorderModule
    @Inject lateinit var feederMonitorNotifications: FeederMonitorNotifications
    @Inject lateinit var navController: NavigationController
    @Inject lateinit var navigationEntries: Set<@JvmSuppressWildcards NavigationEntry>
    @Inject lateinit var networkStateProvider: NetworkStateProvider

    private var showSplashScreen = true
    private var pendingIntent: Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        enableEdgeToEdge()
        // enableEdgeToEdge() on API 35+ skips setDecorFitsSystemWindows(false), assuming
        // the platform enforces edge-to-edge. But enforcement only applies to targetSdk >= 35.
        // With targetSdk 34, we must use legacy flags to lay out behind system bars.
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        super.onCreate(savedInstanceState)

        splashScreen.setKeepOnScreenCondition { showSplashScreen && savedInstanceState == null }

        feederMonitorNotifications.clearOfflineNotifications()
        pendingIntent = intent
        vm.onGo()

        setContent {
            // Prime WindowInsets before first layout to prevent 0-inset first composition
            val primedInsets = WindowInsets.safeDrawing
            LaunchedEffect(Unit) {
                log(TAG) { "WindowInsets primed: $primedInsets" }
            }

            val themeState by vm.themeState.collectAsState(initial = ThemeState())

            CompositionLocalProvider(
                LocalNavigationEventDispatcherOwner provides this@MainActivity,
            ) {
                AplTheme(state = themeState) {
                    val backStack = rememberNavBackStack(DestinationMain)
                    val isInternetAvailable by networkStateProvider.networkState
                        .map { it.isInternetAvailable }
                        .collectAsState(initial = true)

                    LaunchedEffect(Unit) {
                        navController.setup(backStack)
                        showSplashScreen = false
                        pendingIntent?.let {
                            pendingIntent = null
                            handleIntent(it)
                        }
                    }

                    CompositionLocalProvider(
                        LocalNavigationController provides navController,
                        LocalIsInternetAvailable provides isInternetAvailable,
                    ) {
                        NavigationEventHandler(vm)
                        ErrorEventHandler(vm)

                        val updateRelease by vm.updateRelease.collectAsState()
                        val release = updateRelease
                        if (release != null) {
                            UpdateDialog(
                                release = release,
                                onDismiss = { vm.snoozeUpdate(release) },
                            )
                        }

                        Box(modifier = Modifier.fillMaxSize()) {
                            NavDisplay(
                                backStack = backStack,
                                sceneStrategy = BottomSheetSceneStrategy()
                                    .then(androidx.navigation3.scene.SinglePaneSceneStrategy()),
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
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (!navController.isReady) {
            log(TAG, WARN) { "navController not ready, dropping intent: $intent" }
            return
        }
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

@Composable
private fun UpdateDialog(
    release: GithubApi.ReleaseInfo,
    onDismiss: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var dismissed by remember { mutableStateOf(false) }

    if (dismissed) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }

    AlertDialog(
        onDismissRequest = { dismissed = true },
        title = { Text(stringResource(R.string.update_available_dialog_title)) },
        text = { Text(stringResource(R.string.update_available_dialog_message)) },
        confirmButton = {
            val apkAsset = release.assets.find { it.name.endsWith(".apk", ignoreCase = true) }
            if (apkAsset != null) {
                TextButton(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW).apply { data = apkAsset.downloadUrl.toUri() }
                    context.startActivity(intent)
                }) {
                    Text(stringResource(R.string.update_available_download_action))
                }
            }
            TextButton(onClick = {
                val intent = Intent(Intent.ACTION_VIEW).apply { data = release.htmlUrl.toUri() }
                context.startActivity(intent)
            }) {
                Text(stringResource(R.string.update_available_show_action))
            }
        },
        dismissButton = {
            TextButton(onClick = { dismissed = true }) {
                Text(stringResource(R.string.common_dismiss_action))
            }
        },
    )
}
