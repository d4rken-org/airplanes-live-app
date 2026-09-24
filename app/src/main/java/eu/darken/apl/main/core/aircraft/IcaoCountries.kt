package eu.darken.apl.main.core.aircraft

import java.util.Locale

/**
 * The state an ICAO 24-bit address was allocated to, e.g. `3C65A3` is Germany. Ranges are checked
 * in order and the first match wins, some territories sit inside their state's range.
 *
 * Ranges from tar1090's `flags.js` (GPL-2.0-or-later), generated from ICAO Annex 10 Vol III.
 */
object IcaoCountries {

    /** ISO 3166 alpha-2 code, or null for unallocated and ICAO-reserved ranges. */
    fun countryCode(hex: AircraftHex): String? {
        // Non-ICAO addresses carry a ~ prefix and belong to no state
        val address = hex.toIntOrNull(16) ?: return null
        for (i in STARTS.indices) {
            if (address in STARTS[i]..ENDS[i]) return CODES[i]
        }
        return null
    }

    fun countryName(hex: AircraftHex, locale: Locale = Locale.getDefault()): String? =
        countryCode(hex)
            ?.let { Locale.Builder().setRegion(it.uppercase()).build().getDisplayCountry(locale) }
            ?.takeIf { it.isNotBlank() }

    private val STARTS = intArrayOf(
        0x004000, // Zimbabwe
        0x006000, // Mozambique
        0x008000, // South Africa
        0x010000, // Egypt
        0x018000, // Libya
        0x020000, // Morocco
        0x028000, // Tunisia
        0x030000, // Botswana
        0x032000, // Burundi
        0x034000, // Cameroon
        0x035000, // Comoros
        0x036000, // Republic of the Congo
        0x038000, // Côte d’Ivoire
        0x03E000, // Gabon
        0x040000, // Ethiopia
        0x042000, // Equatorial Guinea
        0x044000, // Ghana
        0x046000, // Guinea
        0x048000, // Guinea-Bissau
        0x04A000, // Lesotho
        0x04C000, // Kenya
        0x050000, // Liberia
        0x054000, // Madagascar
        0x058000, // Malawi
        0x05A000, // Maldives
        0x05C000, // Mali
        0x05E000, // Mauritania
        0x060000, // Mauritius
        0x062000, // Niger
        0x064000, // Nigeria
        0x068000, // Uganda
        0x06A000, // Qatar
        0x06C000, // Central African Republic
        0x06E000, // Rwanda
        0x070000, // Senegal
        0x074000, // Seychelles
        0x076000, // Sierra Leone
        0x078000, // Somalia
        0x07A000, // Eswatini
        0x07C000, // Sudan
        0x080000, // Tanzania
        0x084000, // Chad
        0x088000, // Togo
        0x08A000, // Zambia
        0x08C000, // DR Congo
        0x090000, // Angola
        0x094000, // Benin
        0x096000, // Cabo Verde
        0x098000, // Djibouti
        0x09A000, // Gambia
        0x09C000, // Burkina Faso
        0x09E000, // São Tomé and Príncipe
        0x0A0000, // Algeria
        0x0A8000, // Bahamas
        0x0AA000, // Barbados
        0x0AB000, // Belize
        0x0AC000, // Colombia
        0x0AE000, // Costa Rica
        0x0B0000, // Cuba
        0x0B2000, // El Salvador
        0x0B4000, // Guatemala
        0x0B6000, // Guyana
        0x0B8000, // Haiti
        0x0BA000, // Honduras
        0x0BC000, // Saint Vincent and the Grenadines
        0x0BE000, // Jamaica
        0x0C0000, // Nicaragua
        0x0C2000, // Panama
        0x0C4000, // Dominican Republic
        0x0C6000, // Trinidad and Tobago
        0x0C8000, // Suriname
        0x0CA000, // Antigua and Barbuda
        0x0CC000, // Grenada
        0x0D0000, // Mexico
        0x0D8000, // Venezuela
        0x100000, // Russia
        0x201000, // Namibia
        0x202000, // Eritrea
        0x300000, // Italy
        0x340000, // Spain
        0x380000, // France
        0x3C0000, // Germany
        0x400000, // Bermuda
        0x4001C0, // Cayman Islands
        0x400300, // Turks and Caicos Islands
        0x424135, // Cayman Islands
        0x424200, // Bermuda
        0x424700, // Cayman Islands
        0x424B00, // Isle of Man
        0x43BE00, // Bermuda
        0x43E700, // Isle of Man
        0x43EAFE, // Guernsey
        0x400000, // United Kingdom
        0x440000, // Austria
        0x448000, // Belgium
        0x450000, // Bulgaria
        0x458000, // Denmark
        0x460000, // Finland
        0x468000, // Greece
        0x470000, // Hungary
        0x478000, // Norway
        0x480000, // Netherlands
        0x488000, // Poland
        0x490000, // Portugal
        0x498000, // Czechia
        0x4A0000, // Romania
        0x4A8000, // Sweden
        0x4B0000, // Switzerland
        0x4B8000, // Turkey
        0x4C0000, // Serbia
        0x4C8000, // Cyprus
        0x4CA000, // Ireland
        0x4CC000, // Iceland
        0x4D0000, // Luxembourg
        0x4D2000, // Malta
        0x4D4000, // Monaco
        0x500000, // San Marino
        0x501000, // Albania
        0x501800, // Croatia
        0x502800, // Latvia
        0x503800, // Lithuania
        0x504800, // Moldova
        0x505800, // Slovakia
        0x506800, // Slovenia
        0x507800, // Uzbekistan
        0x508000, // Ukraine
        0x510000, // Belarus
        0x511000, // Estonia
        0x512000, // North Macedonia
        0x513000, // Bosnia and Herzegovina
        0x514000, // Georgia
        0x515000, // Tajikistan
        0x516000, // Montenegro
        0x600000, // Armenia
        0x600800, // Azerbaijan
        0x601000, // Kyrgyzstan
        0x601800, // Turkmenistan
        0x680000, // Bhutan
        0x681000, // Micronesia, Federated States of
        0x682000, // Mongolia
        0x683000, // Kazakhstan
        0x684000, // Palau
        0x700000, // Afghanistan
        0x702000, // Bangladesh
        0x704000, // Myanmar
        0x706000, // Kuwait
        0x708000, // Laos
        0x70A000, // Nepal
        0x70C000, // Oman
        0x70E000, // Cambodia
        0x710000, // Saudi Arabia
        0x718000, // South Korea
        0x720000, // North Korea
        0x728000, // Iraq
        0x730000, // Iran
        0x738000, // Israel
        0x740000, // Jordan
        0x748000, // Lebanon
        0x750000, // Malaysia
        0x758000, // Philippines
        0x760000, // Pakistan
        0x768000, // Singapore
        0x770000, // Sri Lanka
        0x778000, // Syria
        0x789000, // Hong Kong
        0x780000, // China
        0x7C0000, // Australia
        0x800000, // India
        0x840000, // Japan
        0x880000, // Thailand
        0x888000, // Viet Nam
        0x890000, // Yemen
        0x894000, // Bahrain
        0x895000, // Brunei
        0x896000, // United Arab Emirates
        0x897000, // Solomon Islands
        0x898000, // Papua New Guinea
        0x899000, // Taiwan
        0x8A0000, // Indonesia
        0x900000, // Marshall Islands
        0x901000, // Cook Islands
        0x902000, // Samoa
        0xA00000, // United States
        0xC00000, // Canada
        0xC80000, // New Zealand
        0xC88000, // Fiji
        0xC8A000, // Nauru
        0xC8C000, // Saint Lucia
        0xC8D000, // Tonga
        0xC8E000, // Kiribati
        0xC90000, // Vanuatu
        0xC91000, // Andorra
        0xC92000, // Dominica
        0xC93000, // Saint Kitts and Nevis
        0xC94000, // South Sudan
        0xC95000, // Timor-Leste
        0xC97000, // Tuvalu
        0xE00000, // Argentina
        0xE40000, // Brazil
        0xE80000, // Chile
        0xE84000, // Ecuador
        0xE88000, // Paraguay
        0xE8C000, // Peru
        0xE90000, // Uruguay
        0xE94000, // Bolivia
        0xF00000, // ICAO (temporary)
        0xF09000, // ICAO (special use)
    )

    private val ENDS = intArrayOf(
        0x0047FF,
        0x006FFF,
        0x00FFFF,
        0x017FFF,
        0x01FFFF,
        0x027FFF,
        0x02FFFF,
        0x0307FF,
        0x032FFF,
        0x034FFF,
        0x0357FF,
        0x036FFF,
        0x038FFF,
        0x03EFFF,
        0x040FFF,
        0x042FFF,
        0x044FFF,
        0x046FFF,
        0x0487FF,
        0x04A7FF,
        0x04CFFF,
        0x050FFF,
        0x054FFF,
        0x058FFF,
        0x05A7FF,
        0x05CFFF,
        0x05E7FF,
        0x0607FF,
        0x062FFF,
        0x064FFF,
        0x068FFF,
        0x06AFFF,
        0x06CFFF,
        0x06EFFF,
        0x070FFF,
        0x0747FF,
        0x0767FF,
        0x078FFF,
        0x07A7FF,
        0x07CFFF,
        0x080FFF,
        0x084FFF,
        0x088FFF,
        0x08AFFF,
        0x08CFFF,
        0x090FFF,
        0x0947FF,
        0x0967FF,
        0x0987FF,
        0x09AFFF,
        0x09CFFF,
        0x09E7FF,
        0x0A7FFF,
        0x0A8FFF,
        0x0AA7FF,
        0x0AB7FF,
        0x0ADFFF,
        0x0AEFFF,
        0x0B0FFF,
        0x0B2FFF,
        0x0B4FFF,
        0x0B6FFF,
        0x0B8FFF,
        0x0BAFFF,
        0x0BC7FF,
        0x0BEFFF,
        0x0C0FFF,
        0x0C2FFF,
        0x0C4FFF,
        0x0C6FFF,
        0x0C8FFF,
        0x0CA7FF,
        0x0CC7FF,
        0x0D7FFF,
        0x0DFFFF,
        0x1FFFFF,
        0x2017FF,
        0x2027FF,
        0x33FFFF,
        0x37FFFF,
        0x3BFFFF,
        0x3FFFFF,
        0x4001BF,
        0x4001FF,
        0x4003FF,
        0x4241F2,
        0x4246FF,
        0x424899,
        0x424BFF,
        0x43BEFF,
        0x43EAFD,
        0x43EEFF,
        0x43FFFF,
        0x447FFF,
        0x44FFFF,
        0x457FFF,
        0x45FFFF,
        0x467FFF,
        0x46FFFF,
        0x477FFF,
        0x47FFFF,
        0x487FFF,
        0x48FFFF,
        0x497FFF,
        0x49FFFF,
        0x4A7FFF,
        0x4AFFFF,
        0x4B7FFF,
        0x4BFFFF,
        0x4C7FFF,
        0x4C87FF,
        0x4CAFFF,
        0x4CCFFF,
        0x4D07FF,
        0x4D27FF,
        0x4D47FF,
        0x5007FF,
        0x5017FF,
        0x501FFF,
        0x502FFF,
        0x503FFF,
        0x504FFF,
        0x505FFF,
        0x506FFF,
        0x507FFF,
        0x50FFFF,
        0x5107FF,
        0x5117FF,
        0x5127FF,
        0x5137FF,
        0x5147FF,
        0x5157FF,
        0x5167FF,
        0x6007FF,
        0x600FFF,
        0x6017FF,
        0x601FFF,
        0x6807FF,
        0x6817FF,
        0x6827FF,
        0x6837FF,
        0x6847FF,
        0x700FFF,
        0x702FFF,
        0x704FFF,
        0x706FFF,
        0x708FFF,
        0x70AFFF,
        0x70C7FF,
        0x70EFFF,
        0x717FFF,
        0x71FFFF,
        0x727FFF,
        0x72FFFF,
        0x737FFF,
        0x73FFFF,
        0x747FFF,
        0x74FFFF,
        0x757FFF,
        0x75FFFF,
        0x767FFF,
        0x76FFFF,
        0x777FFF,
        0x77FFFF,
        0x789FFF,
        0x7BFFFF,
        0x7FFFFF,
        0x83FFFF,
        0x87FFFF,
        0x887FFF,
        0x88FFFF,
        0x890FFF,
        0x894FFF,
        0x8957FF,
        0x896FFF,
        0x8977FF,
        0x898FFF,
        0x8997FF,
        0x8A7FFF,
        0x9007FF,
        0x9017FF,
        0x9027FF,
        0xAFFFFF,
        0xC3FFFF,
        0xC87FFF,
        0xC88FFF,
        0xC8A7FF,
        0xC8C7FF,
        0xC8D7FF,
        0xC8E7FF,
        0xC907FF,
        0xC917FF,
        0xC927FF,
        0xC937FF,
        0xC947FF,
        0xC957FF,
        0xC977FF,
        0xE3FFFF,
        0xE7FFFF,
        0xE80FFF,
        0xE84FFF,
        0xE88FFF,
        0xE8CFFF,
        0xE90FFF,
        0xE94FFF,
        0xF07FFF,
        0xF097FF,
    )

    private val CODES = arrayOf(
        "zw",
        "mz",
        "za",
        "eg",
        "ly",
        "ma",
        "tn",
        "bw",
        "bi",
        "cm",
        "km",
        "cg",
        "ci",
        "ga",
        "et",
        "gq",
        "gh",
        "gn",
        "gw",
        "ls",
        "ke",
        "lr",
        "mg",
        "mw",
        "mv",
        "ml",
        "mr",
        "mu",
        "ne",
        "ng",
        "ug",
        "qa",
        "cf",
        "rw",
        "sn",
        "sc",
        "sl",
        "so",
        "sz",
        "sd",
        "tz",
        "td",
        "tg",
        "zm",
        "cd",
        "ao",
        "bj",
        "cv",
        "dj",
        "gm",
        "bf",
        "st",
        "dz",
        "bs",
        "bb",
        "bz",
        "co",
        "cr",
        "cu",
        "sv",
        "gt",
        "gy",
        "ht",
        "hn",
        "vc",
        "jm",
        "ni",
        "pa",
        "do",
        "tt",
        "sr",
        "ag",
        "gd",
        "mx",
        "ve",
        "ru",
        "na",
        "er",
        "it",
        "es",
        "fr",
        "de",
        "bm",
        "ky",
        "tc",
        "ky",
        "bm",
        "ky",
        "im",
        "bm",
        "im",
        "gg",
        "gb",
        "at",
        "be",
        "bg",
        "dk",
        "fi",
        "gr",
        "hu",
        "no",
        "nl",
        "pl",
        "pt",
        "cz",
        "ro",
        "se",
        "ch",
        "tr",
        "rs",
        "cy",
        "ie",
        "is",
        "lu",
        "mt",
        "mc",
        "sm",
        "al",
        "hr",
        "lv",
        "lt",
        "md",
        "sk",
        "si",
        "uz",
        "ua",
        "by",
        "ee",
        "mk",
        "ba",
        "ge",
        "tj",
        "me",
        "am",
        "az",
        "kg",
        "tm",
        "bt",
        "fm",
        "mn",
        "kz",
        "pw",
        "af",
        "bd",
        "mm",
        "kw",
        "la",
        "np",
        "om",
        "kh",
        "sa",
        "kr",
        "kp",
        "iq",
        "ir",
        "il",
        "jo",
        "lb",
        "my",
        "ph",
        "pk",
        "sg",
        "lk",
        "sy",
        "hk",
        "cn",
        "au",
        "in",
        "jp",
        "th",
        "vn",
        "ye",
        "bh",
        "bn",
        "ae",
        "sb",
        "pg",
        "tw",
        "id",
        "mh",
        "ck", // tar1090 has "sk" here, which is Slovakia
        "ws",
        "us",
        "ca",
        "nz",
        "fj",
        "nr",
        "lc",
        "to",
        "ki",
        "vu",
        "ad",
        "dm",
        "kn",
        "ss",
        "tl",
        "tv",
        "ar",
        "br",
        "cl",
        "ec",
        "py",
        "pe",
        "uy",
        "bo",
        null,
        null,
    )
}
