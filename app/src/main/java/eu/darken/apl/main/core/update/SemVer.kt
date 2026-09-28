package eu.darken.apl.main.core.update

data class SemVer(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: String? = null,
    val buildMetadata: String? = null,
) : Comparable<SemVer> {

    override fun compareTo(other: SemVer): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        if (patch != other.patch) return patch.compareTo(other.patch)

        return when {
            preRelease == null && other.preRelease == null -> 0
            preRelease == null -> 1
            other.preRelease == null -> -1
            else -> comparePreRelease(preRelease, other.preRelease)
        }
    }

    override fun toString(): String = buildString {
        append("$major.$minor.$patch")
        if (preRelease != null) append("-$preRelease")
        if (buildMetadata != null) append("+$buildMetadata")
    }

    companion object {
        private val PATTERN = Regex(
            """^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)""" +
                """(?:-((?:0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\.(?:0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?""" +
                """(?:\+([0-9a-zA-Z-]+(?:\.[0-9a-zA-Z-]+)*))?$"""
        )

        fun parse(version: String): SemVer {
            val match = PATTERN.matchEntire(version)
                ?: throw IllegalArgumentException("Invalid version string [$version]")
            val groups = match.groupValues
            return SemVer(
                major = Integer.parseInt(groups[1]),
                minor = Integer.parseInt(groups[2]),
                patch = Integer.parseInt(groups[3]),
                preRelease = groups[4].ifEmpty { null },
                buildMetadata = groups[5].ifEmpty { null },
            )
        }

        private fun comparePreRelease(a: String, b: String): Int {
            val aIds = a.split('.')
            val bIds = b.split('.')
            for (i in 0 until minOf(aIds.size, bIds.size)) {
                val result = compareIdentifier(aIds[i], bIds[i])
                if (result != 0) return result
            }
            return aIds.size.compareTo(bIds.size)
        }

        private fun compareIdentifier(a: String, b: String): Int {
            val aNumeric = a.all { it in '0'..'9' }
            val bNumeric = b.all { it in '0'..'9' }
            return when {
                aNumeric && bNumeric -> try {
                    a.toLong().compareTo(b.toLong())
                } catch (e: NumberFormatException) {
                    // Beyond Long range (e.g. "99999999999999999999"): compareTo must not throw, so order lexically
                    a.compareTo(b)
                }

                aNumeric -> -1
                bNumeric -> 1
                else -> a.compareTo(b)
            }
        }
    }
}
