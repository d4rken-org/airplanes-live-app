package eu.darken.apl.main.core

import androidx.annotation.StringRes
import eu.darken.apl.R
import eu.darken.apl.common.preferences.EnumPreference
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Duration

@Serializable
enum class BackgroundSyncLevel(
    val interval: Duration,
    override val labelRes: Int,
    @StringRes val descriptionRes: Int,
) : EnumPreference<BackgroundSyncLevel> {
    @SerialName("FAST") FAST(
        Duration.ofMinutes(15),
        R.string.background_sync_level_fast_label,
        R.string.background_sync_level_fast_description,
    ),
    @SerialName("BALANCED") BALANCED(
        Duration.ofMinutes(60),
        R.string.background_sync_level_balanced_label,
        R.string.background_sync_level_balanced_description,
    ),
    @SerialName("SLOW") SLOW(
        Duration.ofHours(6),
        R.string.background_sync_level_slow_label,
        R.string.background_sync_level_slow_description,
    ),
    ;

    companion object {
        /**
         * [chosen] is null when the user never picked a level. A stored [FAST] is kept while Pro is
         * missing, so it applies again as soon as Pro returns.
         */
        fun effective(chosen: BackgroundSyncLevel?, isPro: Boolean): BackgroundSyncLevel = when (chosen) {
            null, FAST -> if (isPro) FAST else BALANCED
            BALANCED, SLOW -> chosen
        }
    }
}
