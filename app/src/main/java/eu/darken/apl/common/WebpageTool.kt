package eu.darken.apl.common

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.apl.common.debug.logging.Logging.Priority.ERROR
import eu.darken.apl.common.debug.logging.log
import javax.inject.Inject

@Reusable
class WebpageTool @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    fun open(address: String) {
        // Intent filters match schemes case-sensitively, so "HTTPS://..." would find no browser
        val intent = Intent(Intent.ACTION_VIEW, address.toUri().normalizeScheme()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            log(ERROR) { "Failed to launch" }
        }
    }

}