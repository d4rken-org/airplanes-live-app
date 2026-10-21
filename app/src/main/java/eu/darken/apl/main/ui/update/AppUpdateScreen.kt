package eu.darken.apl.main.ui.update

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.apl.R
import eu.darken.apl.common.compose.Preview2
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.common.error.ErrorEventHandler
import eu.darken.apl.common.navigation.LocalNavigationController
import eu.darken.apl.common.navigation.NavigationEventHandler
import eu.darken.apl.main.core.update.AppUpdatePrompt.Prompt

@Composable
fun AppUpdateScreenHost(
    vm: AppUpdateViewModel = hiltViewModel(),
) {
    val navController = LocalNavigationController.current ?: return

    NavigationEventHandler(vm)
    ErrorEventHandler(vm)

    val state by vm.prompt.collectAsStateWithLifecycle(initialValue = UpdatePromptState.NotKnown)
    val isChecking by vm.isChecking.collectAsStateWithLifecycle()

    val current = when (val known = state) {
        UpdatePromptState.NotKnown -> return
        is UpdatePromptState.Known -> known.prompt
    }

    if (current == null) {
        LaunchedEffect(Unit) { navController.enterTabs() }
        return
    }

    BackHandler {
        when (current) {
            is Prompt.Recommended -> vm.notNow(current.version)
            Prompt.Required -> vm.finish()
        }
    }

    AppUpdateScreen(
        prompt = current,
        isChecking = isChecking,
        onUpdate = { vm.update() },
        onNotNow = { if (current is Prompt.Recommended) vm.notNow(current.version) },
        onCheckAgain = { vm.checkAgain() },
        onContinueLimited = { vm.continueLimited() },
    )
}

@Composable
fun AppUpdateScreen(
    prompt: Prompt,
    isChecking: Boolean,
    onUpdate: () -> Unit,
    onNotNow: () -> Unit,
    onCheckAgain: () -> Unit,
    onContinueLimited: () -> Unit,
) {
    Scaffold { contentPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(32.dp),
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
                    text = stringResource(
                        when (prompt) {
                            is Prompt.Recommended -> R.string.update_prompt_recommended_title
                            Prompt.Required -> R.string.update_prompt_required_title
                        }
                    ),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    text = stringResource(
                        when (prompt) {
                            is Prompt.Recommended -> R.string.update_prompt_recommended_msg
                            Prompt.Required -> R.string.update_prompt_required_msg
                        }
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (prompt == Prompt.Required) {
                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = stringResource(R.string.update_prompt_required_limited_msg),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onUpdate,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.update_prompt_update_action))
                }

                when (prompt) {
                    is Prompt.Recommended -> {
                        TextButton(
                            onClick = onNotNow,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.update_prompt_not_now_action))
                        }
                    }

                    Prompt.Required -> {
                        OutlinedButton(
                            onClick = onCheckAgain,
                            enabled = !isChecking,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (isChecking) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(stringResource(R.string.update_prompt_check_again_action))
                        }

                        TextButton(
                            onClick = onContinueLimited,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = stringResource(R.string.update_prompt_continue_limited_action),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview2
@Composable
private fun AppUpdateScreenRecommendedPreview() {
    PreviewWrapper {
        AppUpdateScreen(
            prompt = Prompt.Recommended(version = 10200),
            isChecking = false,
            onUpdate = {},
            onNotNow = {},
            onCheckAgain = {},
            onContinueLimited = {},
        )
    }
}

@Preview2
@Composable
private fun AppUpdateScreenRequiredPreview() {
    PreviewWrapper {
        AppUpdateScreen(
            prompt = Prompt.Required,
            isChecking = true,
            onUpdate = {},
            onNotNow = {},
            onCheckAgain = {},
            onContinueLimited = {},
        )
    }
}
