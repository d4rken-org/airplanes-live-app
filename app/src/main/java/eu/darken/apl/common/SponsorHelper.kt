package eu.darken.apl.common

import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SponsorHelper internal constructor(
    private val webpageTool: WebpageTool,
    flavor: BuildConfigWrap.Flavor,
) {

    @Inject constructor(
        webpageTool: WebpageTool,
    ) : this(webpageTool, BuildConfigWrap.FLAVOR)

    // Google Play's payments policy doesn't allow linking to external donation pages
    val isAvailable: Boolean = flavor == BuildConfigWrap.Flavor.FOSS

    suspend fun openSponsorPage() {
        if (!isAvailable) {
            log(TAG, WARN) { "openSponsorPage(): Not available in this build, refusing" }
            return
        }
        webpageTool.open(SPONSOR_URL)
    }

    companion object {
        internal const val SPONSOR_URL = "https://github.com/sponsors/d4rken"
        private val TAG = logTag("Sponsor", "Helper")
    }
}
