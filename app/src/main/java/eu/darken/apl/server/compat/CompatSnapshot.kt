package eu.darken.apl.server.compat

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
internal data class CompatSnapshot(
    /** The build that was judged. A different build starts without a judgement. */
    val appVersionCode: Long,
    val result: Result,
    val recommendedVersion: Int? = null,
    /** When the server's version requirements were last read successfully. A refusal does not move it. */
    @Contextual val checkedAt: Instant? = null,
) {

    enum class Result {
        OK,
        RECOMMENDED,
        REQUIRED,
        ;
    }
}
