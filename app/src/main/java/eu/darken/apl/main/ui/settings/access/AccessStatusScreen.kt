package eu.darken.apl.main.ui.settings.access

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.apl.R
import eu.darken.apl.common.compose.aplContentWindowInsets
import eu.darken.apl.common.error.ErrorEventHandler
import eu.darken.apl.common.navigation.LocalNavigationController
import eu.darken.apl.common.navigation.NavigationEventHandler
import eu.darken.apl.server.access.AccessState
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun AccessStatusScreenHost(
    vm: AccessStatusViewModel = hiltViewModel(),
) {
    NavigationEventHandler(vm)
    ErrorEventHandler(vm)

    val state by vm.state.collectAsState(initial = null)

    AccessStatusScreen(
        state = state ?: AccessStatusViewModel.State(),
        onRefresh = vm::refresh,
        onResetIdentity = vm::resetIdentity,
    )
}

@Composable
fun AccessStatusScreen(
    state: AccessStatusViewModel.State,
    onRefresh: () -> Unit,
    onResetIdentity: () -> Unit,
) {
    val navController = LocalNavigationController.current
    Scaffold(
        contentWindowInsets = aplContentWindowInsets(hasBottomNav = false),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.access_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController?.up() }) {
                        Icon(Icons.AutoMirrored.TwoTone.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(16.dp),
        ) {
            val access = state.access
            Text(
                text = stringResource(
                    R.string.access_settings_tier_x,
                    when (access?.tier) {
                        AccessState.Tier.FEEDER -> "Feeder"
                        else -> "Free"
                    },
                ),
                style = MaterialTheme.typography.titleMedium,
            )

            if (access?.restricted == true) {
                Text(
                    text = stringResource(R.string.access_settings_restricted),
                    color = MaterialTheme.colorScheme.error,
                )
            }

            state.installationId?.let { installationId ->
                Text(
                    text = stringResource(
                        R.string.access_settings_installation_x,
                        installationId.take(8),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            access?.usage?.let { usage ->
                Text(stringResource(R.string.access_settings_viewing_x_of_y, usage.viewing.used, usage.viewing.limit))
                Text(stringResource(R.string.access_settings_search_x_of_y, usage.search.used, usage.search.limit))
                Text(stringResource(R.string.access_settings_watch_x_of_y, usage.watch.used, usage.watch.limit))
                Text(
                    text = stringResource(
                        R.string.access_settings_resets_x,
                        RESET_FORMAT.format(access.resetsAt.atZone(ZoneId.systemDefault())),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            TextButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.access_settings_refresh_action))
            }

            if (state.canResetIdentity) {
                TextButton(onClick = onResetIdentity, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.access_settings_reset_identity_action))
                }
            }
        }
    }
}

private val RESET_FORMAT: DateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
