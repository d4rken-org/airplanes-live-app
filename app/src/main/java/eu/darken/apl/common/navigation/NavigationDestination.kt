package eu.darken.apl.common.navigation

import androidx.navigation3.runtime.NavKey

interface NavigationDestination : NavKey

/** Shown before the tabs, e.g. the onboarding. */
interface BootstrapDestination : NavigationDestination
