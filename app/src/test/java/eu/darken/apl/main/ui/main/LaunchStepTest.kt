package eu.darken.apl.main.ui.main

import eu.darken.apl.main.core.update.AppUpdatePrompt.Prompt
import eu.darken.apl.main.ui.update.UpdatePromptState
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class LaunchStepTest : BaseTest() {

    private val nothingToAsk = UpdatePromptState.Known(null)
    private val required = UpdatePromptState.Known(Prompt.Required)
    private val recommended = UpdatePromptState.Known(Prompt.Recommended(version = 110))

    @Test
    fun `waits while the onboarding state is unknown`() {
        launchStep(isOnboardingFinished = null, updatePrompt = UpdatePromptState.NotKnown) shouldBe LaunchStep.WAIT
        launchStep(isOnboardingFinished = null, updatePrompt = required) shouldBe LaunchStep.WAIT
        launchStep(isOnboardingFinished = null, updatePrompt = nothingToAsk) shouldBe LaunchStep.WAIT
    }

    @Test
    fun `an unfinished onboarding goes first`() {
        launchStep(isOnboardingFinished = false, updatePrompt = required) shouldBe LaunchStep.ONBOARDING
        launchStep(isOnboardingFinished = false, updatePrompt = recommended) shouldBe LaunchStep.ONBOARDING
        launchStep(isOnboardingFinished = false, updatePrompt = nothingToAsk) shouldBe LaunchStep.ONBOARDING
    }

    @Test
    fun `an unfinished onboarding does not wait for the update prompt`() {
        launchStep(isOnboardingFinished = false, updatePrompt = UpdatePromptState.NotKnown) shouldBe LaunchStep.ONBOARDING
    }

    @Test
    fun `waits for the update prompt after the onboarding`() {
        launchStep(isOnboardingFinished = true, updatePrompt = UpdatePromptState.NotKnown) shouldBe LaunchStep.WAIT
    }

    @Test
    fun `a required update shows the update page`() {
        launchStep(isOnboardingFinished = true, updatePrompt = required) shouldBe LaunchStep.UPDATE
    }

    @Test
    fun `a recommended update shows the update page`() {
        launchStep(isOnboardingFinished = true, updatePrompt = recommended) shouldBe LaunchStep.UPDATE
    }

    @Test
    fun `nothing to ask goes to the tabs`() {
        launchStep(isOnboardingFinished = true, updatePrompt = nothingToAsk) shouldBe LaunchStep.TABS
    }
}
