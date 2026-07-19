package eu.darken.apl.account.core.auth

/**
 * Builds a human-readable device label for the airplanes.live OAuth session list (the `device_name`
 * field sent on the initial token exchange). Tolerates blank / "unknown" [android.os.Build] values
 * and returns `null` when nothing usable can be derived — the server then falls back to deriving a
 * label from the User-Agent.
 */
object DeviceName {

    private const val MAX_LENGTH = 120

    /**
     * Combines [manufacturer] and [model] into a clean label, e.g. `Google` + `Pixel 8` -> "Google
     * Pixel 8", while preserving model casing (`SM-G991B` must not be mangled) and avoiding a
     * duplicated manufacturer when the model already starts with it.
     */
    fun format(manufacturer: String?, model: String?): String? {
        val mfr = manufacturer.normalize()
        val mdl = model.normalize()
        val label = when {
            mfr == null && mdl == null -> return null
            mdl == null -> mfr!!.capitalizeFirst()
            mfr == null -> mdl
            mdl.startsWith(mfr, ignoreCase = true) -> mdl
            else -> "${mfr.capitalizeFirst()} $mdl"
        }
        return label.truncateSafe().ifBlank { null }
    }

    /**
     * [MAX_LENGTH] is a server-side *character* limit; a plain take() counts UTF-16 units and can
     * cut between the halves of a surrogate pair, sending a malformed trailing char. Unpaired
     * surrogates never survive [normalize], so only a split pair can leave a dangling high half.
     */
    private fun String.truncateSafe(): String = take(MAX_LENGTH)
        .let { if (it.isNotEmpty() && it.last().isHighSurrogate()) it.dropLast(1) else it }

    private fun String?.normalize(): String? = this
        ?.replace(CONTROL_OR_WHITESPACE, " ")
        ?.trim()
        ?.takeUnless { it.isEmpty() || it.equals("unknown", ignoreCase = true) }

    private fun String.capitalizeFirst(): String =
        replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

    /**
     * Collapses runs of whitespace and category-C code points (C0/C1 controls, zero-width/bidi
     * format chars, unpaired surrogates, private use) to one space. Paired surrogates (emoji) are
     * matched as whole code points by the regex engine and pass through untouched.
     */
    private val CONTROL_OR_WHITESPACE = Regex("[\\p{C}\\s]+")
}
