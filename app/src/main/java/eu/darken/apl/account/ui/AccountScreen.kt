package eu.darken.apl.account.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.AccountCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.apl.R
import eu.darken.apl.common.compose.Preview2
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.common.compose.aplContentWindowInsets
import eu.darken.apl.common.error.ErrorEventHandler
import eu.darken.apl.common.navigation.NavigationEventHandler

@Composable
fun AccountScreenHost(
    vm: AccountViewModel = hiltViewModel(),
) {
    NavigationEventHandler(vm)
    ErrorEventHandler(vm)

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> vm.onLoginResult(result.data) }

    val state by vm.state.collectAsState(initial = null)
    state?.let {
        AccountScreen(
            state = it,
            onBack = { vm.navUp() },
            onLogin = { launcher.launch(vm.buildLoginIntent()) },
            onLogout = { vm.logout() },
            onManage = { vm.openManageAccount() },
        )
    }
}

@Composable
fun AccountScreen(
    state: AccountViewModel.State,
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onManage: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = aplContentWindowInsets(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.account_label)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.TwoTone.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.TwoTone.AccountCircle,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary,
            )

            if (state.isLoggedIn) {
                val identity = state.identity
                Text(
                    text = identity?.displayName
                        ?: identity?.handle
                        ?: identity?.email
                        ?: stringResource(R.string.account_logged_in_label),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                identity?.email?.let {
                    Text(text = it, style = MaterialTheme.typography.bodyMedium)
                }
                OutlinedButton(onClick = onManage, enabled = !state.isBusy) {
                    Text(stringResource(R.string.account_manage_action))
                }
                Button(onClick = onLogout, enabled = !state.isBusy) {
                    Text(stringResource(R.string.account_logout_action))
                }
            } else {
                Text(
                    text = stringResource(R.string.account_login_explanation),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = onLogin, enabled = !state.isBusy) {
                    Text(stringResource(R.string.account_login_action))
                }
            }

            if (state.isBusy) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
            }
        }
    }
}

@Preview2
@Composable
private fun AccountScreenLoggedOutPreview() {
    PreviewWrapper {
        AccountScreen(
            state = AccountViewModel.State(isLoggedIn = false, identity = null, isBusy = false),
            onBack = {}, onLogin = {}, onLogout = {}, onManage = {},
        )
    }
}
