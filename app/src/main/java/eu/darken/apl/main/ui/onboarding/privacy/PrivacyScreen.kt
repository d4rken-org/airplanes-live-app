package eu.darken.apl.main.ui.onboarding.privacy

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.apl.R
import eu.darken.apl.common.compose.Preview2
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.common.error.ErrorEventHandler
import eu.darken.apl.common.navigation.NavigationEventHandler

@Composable
fun PrivacyScreenHost(
    vm: PrivacyViewModel = hiltViewModel(),
) {
    NavigationEventHandler(vm)
    ErrorEventHandler(vm)

    val state by vm.state.collectAsState(initial = null)
    state?.let {
        PrivacyScreen(
            state = it,
            onPrivacyPolicy = { vm.goPrivacyPolicy() },
            onToggleUpdateCheck = { vm.toggleUpdateCheck() },
            onAccept = { vm.finishPrivacy() },
        )
    }
}

@Composable
fun PrivacyScreen(
    state: PrivacyViewModel.State,
    onPrivacyPolicy: () -> Unit,
    onToggleUpdateCheck: () -> Unit,
    onAccept: () -> Unit,
) {
    Scaffold { contentPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(32.dp))

                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(128.dp),
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.privacy_body1),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.privacy_body2),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.privacy_body3),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(16.dp))

                TextButton(onClick = onPrivacyPolicy) {
                    Text(stringResource(R.string.settings_privacy_policy_label))
                }

                if (state.isUpdateCheckSupported) {
                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onToggleUpdateCheck)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_creation_24),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )

                        Spacer(Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.updatecheck_setting_enabled_label),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = stringResource(R.string.updatecheck_setting_enabled_explanation),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }

                        Spacer(Modifier.width(16.dp))

                        Switch(
                            checked = state.isUpdateCheckEnabled,
                            onCheckedChange = null,
                        )
                    }
                }
            }

            Button(
                onClick = onAccept,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 16.dp),
            ) {
                Text(stringResource(R.string.common_accept_action))
            }
        }
    }
}

@Preview2
@Composable
private fun PrivacyScreenUpdateCheckSupportedPreview() {
    PreviewWrapper {
        PrivacyScreen(
            state = PrivacyViewModel.State(
                isUpdateCheckSupported = true,
                isUpdateCheckEnabled = false,
            ),
            onPrivacyPolicy = {},
            onToggleUpdateCheck = {},
            onAccept = {},
        )
    }
}

@Preview2
@Composable
private fun PrivacyScreenNoUpdateCheckPreview() {
    PreviewWrapper {
        PrivacyScreen(
            state = PrivacyViewModel.State(
                isUpdateCheckSupported = false,
                isUpdateCheckEnabled = false,
            ),
            onPrivacyPolicy = {},
            onToggleUpdateCheck = {},
            onAccept = {},
        )
    }
}
