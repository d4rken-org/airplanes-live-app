package eu.darken.apl.main.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.apl.common.error.ErrorEventHandler
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

    if (!isOnboardingFinished) {
        navController.goTo(DestinationWelcome)
        return
    }

    LaunchedEffect(Unit) {
        navController.replace(DestinationMap())
    }
}
