package eu.darken.apl.common.navigation

import androidx.navigation3.runtime.NavKey

interface NavigationDestination : NavKey

/** Backed by app-wide state, so the back stack holds at most one of them. */
interface SingleInstanceDestination : NavigationDestination

/** Drawn over the entry below it, so it leaves the back stack together with that entry. */
interface OverlayDestination : NavigationDestination
