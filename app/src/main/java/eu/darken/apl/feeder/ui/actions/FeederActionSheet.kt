package eu.darken.apl.feeder.ui.actions

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Insights
import androidx.compose.material.icons.twotone.Map
import androidx.compose.material.icons.twotone.Notifications
import androidx.compose.material.icons.twotone.OpenInBrowser
import androidx.compose.material.icons.twotone.SettingsInputAntenna
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.apl.R
import eu.darken.apl.common.error.ErrorEventHandler
import eu.darken.apl.common.navigation.NavigationEventHandler
import eu.darken.apl.common.settings.SettingsSwitchItem
import eu.darken.apl.feeder.ui.labelRes
import java.time.Instant

@Composable
fun FeederActionSheetHost(
    receiverId: String,
    vm: FeederActionViewModel = hiltViewModel(),
) {
    NavigationEventHandler(vm)
    ErrorEventHandler(vm)

    LaunchedEffect(receiverId) {
        vm.init(receiverId)
    }

    val state by vm.state.collectAsState(initial = null)
    val current = state ?: return
    val feeder = current.feeder

    ModalBottomSheet(onDismissRequest = { vm.navUp() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.TwoTone.SettingsInputAntenna,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                ) {
                    Text(text = feeder.label, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = stringResource(feeder.status.labelRes()) + (feeder.country?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (feeder.isOnline) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Text(
                        text = feeder.lastSeen?.let {
                            stringResource(
                                R.string.feeder_last_seen_label,
                                DateUtils.getRelativeTimeSpanString(
                                    it.toEpochMilli(),
                                    Instant.now().toEpochMilli(),
                                    DateUtils.MINUTE_IN_MILLIS,
                                ).toString(),
                            )
                        } ?: stringResource(R.string.feeder_last_seen_never),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            Button(
                onClick = { vm.viewDetails() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Icon(Icons.TwoTone.Insights, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.feeder_action_view_details))
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { vm.showFeedOnMap() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Icon(Icons.TwoTone.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.feeder_show_map_action))
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = { vm.openOnWebsite() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Icon(Icons.TwoTone.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.feeder_open_website_action))
            }

            HorizontalDivider(modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))

            SettingsSwitchItem(
                title = stringResource(R.string.feeder_notifications_toggle_title),
                summary = stringResource(R.string.feeder_notifications_toggle_desc),
                icon = Icons.TwoTone.Notifications,
                checked = !current.isMuted,
                onCheckedChange = { vm.setNotificationsEnabled(it) },
            )
        }
    }
}
