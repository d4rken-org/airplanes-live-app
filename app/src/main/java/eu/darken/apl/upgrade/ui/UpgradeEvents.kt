package eu.darken.apl.upgrade.ui

sealed interface UpgradeEvents {
    data object RestoreFoundNothing : UpgradeEvents

    data object RestoreUnreachable : UpgradeEvents

    data object SupportEmailUnavailable : UpgradeEvents
}
