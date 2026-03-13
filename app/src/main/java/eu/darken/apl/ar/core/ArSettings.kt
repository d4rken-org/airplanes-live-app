package eu.darken.apl.ar.core

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.apl.common.datastore.createValue
import eu.darken.apl.common.debug.logging.logTag
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArSettings @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val Context.dataStore by preferencesDataStore(name = "settings_ar")

    val pollIntervalMs = context.dataStore.createValue("ar.poll.interval.ms", 4000L)
    val maxVisibleLabels = context.dataStore.createValue("ar.max.visible.labels", 15)
    val maxRangeM = context.dataStore.createValue("ar.max.range.m", API_RANGE_50NM)
    val displayRangeNm = context.dataStore.createValue("ar.display.range.nm", DEFAULT_RANGE_NM)
    val orientationSmoothingAlpha = context.dataStore.createValue("ar.orientation.smoothing.alpha", 0.15f)

    companion object {
        internal val TAG = logTag("AR", "Settings")
        private const val API_RANGE_50NM = 50L * 1852L
        const val MIN_RANGE_NM = 1
        const val DEFAULT_RANGE_NM = 20
        const val MAX_RANGE_NM = 50
    }
}
