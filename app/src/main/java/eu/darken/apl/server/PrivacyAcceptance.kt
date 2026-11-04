package eu.darken.apl.server

import eu.darken.apl.main.core.GeneralSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Whether the user accepted the privacy screen that describes what the server receives. */
@Singleton
class PrivacyAcceptance internal constructor(
    private val accepted: Flow<Boolean>,
) {
    @Inject constructor(generalSettings: GeneralSettings) : this(generalSettings.isOnboardingFinished.flow)

    suspend fun await() {
        accepted.first { it }
    }
}
