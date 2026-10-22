package eu.darken.apl.main.core.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.apl.common.BuildConfigWrap
import eu.darken.apl.common.WebpageTool
import eu.darken.apl.common.debug.logging.Logging.Priority.INFO
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import javax.inject.Inject

/** Opens where this build gets its updates: the Play Store for gplay, GitHub releases otherwise. */
@Reusable
class AppUpdateTarget internal constructor(
    private val context: Context,
    private val webpageTool: WebpageTool,
    private val flavor: BuildConfigWrap.Flavor,
    private val applicationId: String,
) {

    @Inject constructor(
        @ApplicationContext context: Context,
        webpageTool: WebpageTool,
    ) : this(context, webpageTool, BuildConfigWrap.FLAVOR, BuildConfigWrap.APPLICATION_ID)

    fun open() {
        when (flavor) {
            BuildConfigWrap.Flavor.GPLAY -> openPlayStore()
            BuildConfigWrap.Flavor.FOSS, BuildConfigWrap.Flavor.NONE -> openWebpage(GITHUB_RELEASES_URL)
        }
    }

    private fun openPlayStore() {
        val url = "market://details?id=$applicationId"
        val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            log(TAG, INFO) { "Opening update target $url" }
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            log(TAG, WARN) { "No store app for $url" }
            openWebpage("https://play.google.com/store/apps/details?id=$applicationId")
        }
    }

    private fun openWebpage(url: String) {
        log(TAG, INFO) { "Opening update target $url" }
        webpageTool.open(url)
    }

    companion object {
        internal const val GITHUB_RELEASES_URL = "https://github.com/d4rken-org/airplanes-live-app/releases/latest"
        private val TAG = logTag("Update", "Target")
    }
}
