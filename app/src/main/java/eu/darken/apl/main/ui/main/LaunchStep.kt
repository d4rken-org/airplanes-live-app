package eu.darken.apl.main.ui.main

import eu.darken.apl.main.ui.update.UpdatePromptState

enum class LaunchStep {
    WAIT,
    ONBOARDING,
    UPDATE,
    TABS,
    ;
}

fun launchStep(isOnboardingFinished: Boolean?, updatePrompt: UpdatePromptState): LaunchStep = when {
    isOnboardingFinished == null -> LaunchStep.WAIT
    !isOnboardingFinished -> LaunchStep.ONBOARDING
    updatePrompt !is UpdatePromptState.Known -> LaunchStep.WAIT
    updatePrompt.prompt != null -> LaunchStep.UPDATE
    else -> LaunchStep.TABS
}
