package eu.darken.apl.ar.core

data class DeviceOrientation(
    val azimuthRad: Float,
    val pitchRad: Float,
    val rollRad: Float,
    val rotationMatrix: FloatArray,
    val displayRotation: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DeviceOrientation) return false
        return azimuthRad == other.azimuthRad &&
                pitchRad == other.pitchRad &&
                rollRad == other.rollRad &&
                rotationMatrix.contentEquals(other.rotationMatrix) &&
                displayRotation == other.displayRotation
    }

    override fun hashCode(): Int {
        var result = azimuthRad.hashCode()
        result = 31 * result + pitchRad.hashCode()
        result = 31 * result + rollRad.hashCode()
        result = 31 * result + rotationMatrix.contentHashCode()
        result = 31 * result + displayRotation
        return result
    }
}
