package eu.darken.apl.search.ui.actions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.apl.R
import eu.darken.apl.common.error.ErrorEventHandler
import eu.darken.apl.common.navigation.NavigationEventHandler
import eu.darken.apl.main.ui.AircraftDetails

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchActionSheetHost(
    hex: String,
    vm: SearchActionViewModel = hiltViewModel(),
) {
    NavigationEventHandler(vm)
    ErrorEventHandler(vm)

    LaunchedEffect(hex) {
        vm.init(hex)
    }

    val state by vm.state.collectAsState(initial = null)
    val currentState = state ?: return

    ModalBottomSheet(onDismissRequest = { vm.navUp() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
        ) {
            AircraftDetails(
                aircraft = currentState.aircraft,
                route = currentState.route,
                distanceInMeter = currentState.distanceInMeter,
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { vm.showMap() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.common_show_on_map_action))
            }

            Button(
                onClick = { vm.showWatch() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (currentState.watch != null) {
                        stringResource(R.string.watch_list_watch_edit_label)
                    } else {
                        stringResource(R.string.watch_list_watch_add_label)
                    }
                )
            }
        }
    }
}
