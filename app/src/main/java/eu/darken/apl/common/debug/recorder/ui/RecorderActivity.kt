package eu.darken.apl.common.debug.recorder.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.Formatter
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.apl.R
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.error.ErrorEventHandler
import eu.darken.apl.common.theming.AplTheme
import eu.darken.apl.common.uix.Activity2

@AndroidEntryPoint
class RecorderActivity : Activity2() {

    private val vm: RecorderViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent.getStringExtra(RECORD_PATH) == null) {
            finish()
            return
        }

        setContent {
            val themeState by vm.themeState.collectAsState()

            AplTheme(state = themeState) {
                ErrorEventHandler(vm)

                LaunchedEffect(vm.shareEvent) {
                    vm.shareEvent.collect { startActivity(it) }
                }

                val state by vm.state.collectAsState()
                RecorderScreen(
                    state = state,
                    onShare = vm::share,
                    onPrivacyPolicy = vm::goPrivacyPolicy,
                    onCancel = ::finish,
                )
            }
        }
    }

    companion object {
        internal val TAG = logTag("Debug", "Log", "RecorderActivity")
        const val RECORD_PATH = "logPath"

        fun getLaunchIntent(context: Context, path: String): Intent {
            val intent = Intent(context, RecorderActivity::class.java)
            intent.putExtra(RECORD_PATH, path)
            return intent
        }
    }
}

@Composable
private fun RecorderScreen(
    state: RecorderViewModel.State,
    onShare: () -> Unit,
    onPrivacyPolicy: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier.padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            colors = CardDefaults.outlinedCardColors(),
            border = CardDefaults.outlinedCardBorder(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.debug_recorder_file_label),
                    style = MaterialTheme.typography.labelMedium,
                )

                state.normalPath?.let {
                    Text(
                        text = it.path,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.debug_debuglog_sensitive_information_message),
                    style = MaterialTheme.typography.bodyMedium,
                )

                Spacer(Modifier.height(8.dp))

                val linkText = buildAnnotatedString {
                    withStyle(
                        SpanStyle(
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline,
                        )
                    ) {
                        append(stringResource(R.string.settings_privacy_policy_label))
                    }
                }
                ClickableText(
                    text = linkText,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    onClick = { onPrivacyPolicy() },
                )

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.debug_debuglog_size_label),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        if (state.normalSize != -1L) {
                            Text(
                                text = Formatter.formatShortFileSize(context, state.normalSize),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = stringResource(R.string.debug_debuglog_size_compressed_label),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        if (state.compressedSize != -1L) {
                            Text(
                                text = Formatter.formatShortFileSize(context, state.compressedSize),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    OutlinedButton(onClick = onCancel) {
                        Text(stringResource(R.string.common_cancel_action))
                    }

                    if (state.loading) {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    } else {
                        Button(onClick = onShare) {
                            Text(stringResource(R.string.common_share_action))
                        }
                    }
                }
            }
        }
    }
}
