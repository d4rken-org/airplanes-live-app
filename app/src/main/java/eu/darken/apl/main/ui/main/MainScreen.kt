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
import eu.darken.apl.main.ui.update.DestinationAppUpdate
import eu.darken.apl.main.ui.update.UpdatePromptState

@Composable
fun MainScreenHost(
    vm: MainViewModel = hiltViewModel(),
) {
    val navController = LocalNavigationController.current ?: return

    NavigationEventHandler(vm)
    ErrorEventHandler(vm)

    // Unknown until read, the tabs hide this screen and with it an onboarding or update page that turns out to be due
    val isOnboardingFinished by vm.isOnboardingFinished.collectAsState(initial = null)
    val updatePrompt by vm.updatePrompt.collectAsState(initial = UpdatePromptState.NotKnown)

    when (launchStep(isOnboardingFinished, updatePrompt)) {
        LaunchStep.WAIT -> Unit
        LaunchStep.ONBOARDING -> LaunchedEffect(Unit) { navController.goTo(DestinationWelcome) }
        LaunchStep.UPDATE -> LaunchedEffect(Unit) { navController.goTo(DestinationAppUpdate) }
        LaunchStep.TABS -> LaunchedEffect(Unit) { navController.enterTabs() }
    }
}
