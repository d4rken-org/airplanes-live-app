package eu.darken.apl.main.ui.onboarding.team

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Groups
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.apl.R
import eu.darken.apl.common.compose.Preview2
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.common.error.ErrorEventHandler
import eu.darken.apl.common.navigation.NavigationEventHandler
import eu.darken.apl.main.ui.onboarding.OnboardingPage
import eu.darken.apl.main.ui.onboarding.OnboardingPageHeader

@Composable
fun TeamScreenHost(
    vm: TeamViewModel = hiltViewModel(),
) {
    NavigationEventHandler(vm)
    ErrorEventHandler(vm)

    TeamScreen(
        isGplay = vm.isGplay,
        onContinue = { vm.finishTeam() },
    )
}

@Composable
fun TeamScreen(
    isGplay: Boolean,
    onContinue: () -> Unit,
) {
    OnboardingPage(
        pageIndex = 1,
        buttonLabel = stringResource(R.string.common_continue_action),
        onButton = onContinue,
    ) {
        Spacer(Modifier.height(32.dp))

        OnboardingPageHeader(
            icon = Icons.TwoTone.Groups,
            title = stringResource(R.string.onboarding_team_title),
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.onboarding_team_body),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = stringResource(
                if (isGplay) R.string.onboarding_team_tiers_gplay else R.string.onboarding_team_tiers_foss
            ),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = stringResource(
                R.string.onboarding_team_free_hint,
                stringResource(R.string.upgrade_tier_chip_free),
            ),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = stringResource(
                R.string.onboarding_team_support,
                stringResource(R.string.label_settings),
                stringResource(R.string.settings_support_label),
            ),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview2
@Composable
private fun TeamScreenFossPreview() {
    PreviewWrapper { TeamScreen(isGplay = false, onContinue = {}) }
}

@Preview2
@Composable
private fun TeamScreenGplayPreview() {
    PreviewWrapper { TeamScreen(isGplay = true, onContinue = {}) }
}
