package eu.darken.apl.common

import java.util.Locale

private val ISO_COUNTRY_CODE = Regex("^[A-Za-z]{2}$")

/** "DE" becomes "Germany" in an English locale, anything that isn't an ISO alpha-2 code stays as it is. */
internal fun displayCountry(value: String?): String? {
    if (value == null || !ISO_COUNTRY_CODE.matches(value)) return value
    return Locale.Builder().setRegion(value.uppercase()).build()
        .getDisplayCountry(Locale.getDefault())
        .takeIf { it.isNotBlank() } ?: value
}

/** "DE" or "de" becomes 🇩🇪 from its regional indicator symbols, anything that isn't two ASCII letters has none. */
internal fun flagEmoji(countryCode: String?): String? {
    if (countryCode == null || !ISO_COUNTRY_CODE.matches(countryCode)) return null
    return countryCode.uppercase(Locale.ROOT)
        .map { Character.toChars(0x1F1E6 + (it - 'A')).concatToString() }
        .joinToString("")
}
