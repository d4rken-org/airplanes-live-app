package eu.darken.apl.server.api

import eu.darken.apl.common.BuildConfigWrap

/** How the server names this build: `play` for the Google Play flavor, `foss` for everything else. */
object ServerVariant {
    val CURRENT: String = if (BuildConfigWrap.FLAVOR == BuildConfigWrap.Flavor.GPLAY) "play" else "foss"
}
