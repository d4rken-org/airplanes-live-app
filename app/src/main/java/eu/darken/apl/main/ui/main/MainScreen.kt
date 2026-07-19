package eu.darken.apl.main.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.apl.common.error.ErrorEventHandler
import eu.darken.apl.common.navigation.LocalNavigationController
import eu.darken.apl.common.navigation.NavigationEventHandler
import eu.darken.apl.main.ui.DestinationMain
import eu.darken.apl.main.ui.DestinationWelcome
import eu.darken.apl.map.ui.DestinationMap

@Composable
fun MainScreenHost(
    vm: MainViewModel = hiltViewModel(),
) {
    val navController = LocalNavigationController.current ?: return

    NavigationEventHandler(vm)
    ErrorEventHandler(vm)

    val isOnboardingFinished by vm.isOnboardingFinished.collectAsState(initial = null)

    when (isOnboardingFinished) {
        // Routing on a guessed initial value races the DataStore read, wait for the real one
        null -> Unit
        // Swap this router out in place: a startup deep link may already sit on top of it,
        // and replacing the stack top would swallow that destination instead
        false -> LaunchedEffect(Unit) { navController.replace(DestinationMain, DestinationWelcome) }
        true -> LaunchedEffect(Unit) { navController.replace(DestinationMain, DestinationMap()) }
    }
}
