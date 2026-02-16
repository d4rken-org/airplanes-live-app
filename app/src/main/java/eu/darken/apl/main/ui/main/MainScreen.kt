package eu.darken.apl.main.ui.main

import android.content.Intent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.apl.R
import eu.darken.apl.common.error.ErrorEventHandler
import eu.darken.apl.common.github.GithubApi
import eu.darken.apl.common.navigation.LocalNavigationController
import eu.darken.apl.common.navigation.NavigationEventHandler
import eu.darken.apl.main.ui.DestinationWelcome
import eu.darken.apl.map.ui.DestinationMap

@Composable
fun MainScreenHost(
    vm: MainViewModel = hiltViewModel(),
) {
    val navController = LocalNavigationController.current ?: return

    NavigationEventHandler(vm)
    ErrorEventHandler(vm)

    val isOnboardingFinished by vm.isOnboardingFinished.collectAsState(initial = true)
    val newRelease by vm.newRelease.collectAsState(initial = null)

    if (!isOnboardingFinished) {
        navController.goTo(DestinationWelcome)
        return
    }

    // Show update dialog if available
    val release = newRelease
    if (release != null) {
        UpdateDialog(
            release = release,
            onDismiss = { navController.replace(DestinationMap()) },
        )
    } else {
        // Auto-redirect to the default tab (Map)
        LaunchedEffect(Unit) {
            navController.replace(DestinationMap())
        }
    }
}

@Composable
private fun UpdateDialog(
    release: GithubApi.ReleaseInfo,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(true) }

    if (!showDialog) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }

    AlertDialog(
        onDismissRequest = {
            showDialog = false
        },
        title = { Text(stringResource(R.string.update_available_dialog_title)) },
        text = { Text(stringResource(R.string.update_available_dialog_message)) },
        confirmButton = {
            TextButton(onClick = {
                val intent = Intent(Intent.ACTION_VIEW).apply { data = release.htmlUrl.toUri() }
                context.startActivity(intent)
                showDialog = false
            }) {
                Text(stringResource(R.string.update_available_show_action))
            }
        },
        dismissButton = {
            val apkAsset = release.assets.find { it.name.endsWith(".apk", ignoreCase = true) }
            if (apkAsset != null) {
                TextButton(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW).apply { data = apkAsset.downloadUrl.toUri() }
                    context.startActivity(intent)
                    showDialog = false
                }) {
                    Text(stringResource(R.string.update_available_download_action))
                }
            } else {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.common_dismiss_action))
                }
            }
        },
    )
}
