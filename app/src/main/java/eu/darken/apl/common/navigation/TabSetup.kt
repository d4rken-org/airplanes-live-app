package eu.darken.apl.common.navigation

/** One of the app's tabs, [id] is what saved state keeps of it. */
interface NavTab {
    val id: String
    val root: NavigationDestination
}

/** The app's tabs and what leads to them, so the navigation here needs to know none of the screens. */
interface TabSetup {
    /** All tabs, each keeps its own back stack whether or not the bar shows it. */
    val tabs: List<NavTab>

    /** The tab the app starts in, and where back from another tab's own screen goes. */
    val startTab: NavTab

    /** Shown before the tabs, e.g. the onboarding starts from it. */
    val bootstrapRoot: NavigationDestination

    /** The tab whose own screen [destination] is, null for screens opened on top of a tab. */
    fun tabOf(destination: NavigationDestination): NavTab?

    /** Hands what [destination] asks its tab to show to that tab, if it asks for anything. */
    fun deliver(destination: NavigationDestination)
}
