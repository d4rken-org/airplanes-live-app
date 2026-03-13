package eu.darken.apl.ar.core

import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.view.Surface
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Singleton
class DeviceOrientationProvider @Inject constructor(
    private val sensorManager: SensorManager,
    private val dispatcherProvider: DispatcherProvider,
) {

    @Volatile
    private var declinationRad: Float = 0f

    @Volatile
    private var displayRotation: Int = Surface.ROTATION_0

    val hasGameSensor: Boolean
        get() = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) != null

    val hasRotationSensor: Boolean
        get() = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null

    fun updateLocation(location: Location) {
        val field = GeomagneticField(
            location.latitude.toFloat(),
            location.longitude.toFloat(),
            location.altitude.toFloat(),
            System.currentTimeMillis()
        )
        declinationRad = Math.toRadians(field.declination.toDouble()).toFloat()
    }

    fun updateDisplayRotation(rotation: Int) {
        displayRotation = rotation
    }

    val orientation: Flow<DeviceOrientation?> = callbackFlow {
        val gameSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        val rotSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        if (gameSensor == null && rotSensor == null) {
            log(TAG) { "No rotation sensors available" }
            trySend(null)
            awaitClose()
            return@callbackFlow
        }

        val useFusion = gameSensor != null && rotSensor != null

        if (useFusion) {
            log(TAG) { "Using complementary fusion (GAME + ROTATION)" }
        } else if (gameSensor != null) {
            log(TAG) { "Using GAME_ROTATION_VECTOR only (no mag correction)" }
        } else {
            log(TAG) { "Fallback: ROTATION_VECTOR only (no gyro)" }
        }

        val headingFusion = HeadingFusion()
        var latestGameAzimuth = 0f
        var hasGameSample = false
        var magAccuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH

        // Low-pass state for rotation-only fallback
        var prevFallbackAzimuth = Float.NaN

        val gameMatrix = FloatArray(16)
        val remappedGame = FloatArray(16)
        val magMatrix = FloatArray(16)
        val remappedMag = FloatArray(16)

        fun axesForRotation(): Pair<Int, Int> = when (displayRotation) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }

        fun applyZRotation(matrix: FloatArray, rad: Float) {
            val cosR = cos(rad)
            val sinR = sin(rad)
            for (j in 0..2) {
                val r0 = matrix[j]
                val r1 = matrix[4 + j]
                matrix[j] = cosR * r0 - sinR * r1
                matrix[4 + j] = sinR * r0 + cosR * r1
            }
        }

        var lastValidHeading = 0f

        fun headingFromMatrix(m: FloatArray): Float {
            val e = -m[2]; val n = -m[6]
            if (e * e + n * n < 1e-4f) return lastValidHeading
            val h = atan2(e.toDouble(), n.toDouble()).toFloat()
            lastValidHeading = h
            return h
        }

        fun pitchFromMatrix(m: FloatArray): Float {
            return atan2((-m[10]).toDouble(), sqrt((m[2] * m[2] + m[6] * m[6]).toDouble())).toFloat()
        }

        fun rollFromMatrix(m: FloatArray): Float {
            return atan2(m[8].toDouble(), m[9].toDouble()).toFloat()
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(gameMatrix, event.values)
                        val (axisX, axisY) = axesForRotation()
                        SensorManager.remapCoordinateSystem(gameMatrix, axisX, axisY, remappedGame)

                        latestGameAzimuth = headingFromMatrix(remappedGame)
                        hasGameSample = true

                        applyZRotation(remappedGame, headingFusion.smoothedOffsetRad)
                        applyZRotation(remappedGame, declinationRad)

                        trySend(
                            DeviceOrientation(
                                azimuthRad = headingFromMatrix(remappedGame),
                                pitchRad = pitchFromMatrix(remappedGame),
                                rollRad = rollFromMatrix(remappedGame),
                                rotationMatrix = remappedGame.copyOf(),
                            )
                        )
                    }

                    Sensor.TYPE_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(magMatrix, event.values)
                        val (axisX, axisY) = axesForRotation()
                        SensorManager.remapCoordinateSystem(magMatrix, axisX, axisY, remappedMag)
                        val magAzimuth = headingFromMatrix(remappedMag)

                        if (useFusion) {
                            if (hasGameSample
                                && magAccuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM
                                && abs(remappedMag[10]) < VERTICAL_THRESHOLD
                            ) {
                                headingFusion.update(latestGameAzimuth, magAzimuth, event.timestamp)
                            }
                        } else {
                            val rawAzimuth = headingFromMatrix(remappedMag)
                            val filtered = if (prevFallbackAzimuth.isNaN()) {
                                rawAzimuth
                            } else {
                                val delta = HeadingFusion.circularDelta(rawAzimuth, prevFallbackAzimuth)
                                prevFallbackAzimuth + FALLBACK_ALPHA * delta
                            }
                            prevFallbackAzimuth = filtered

                            applyZRotation(remappedMag, filtered - rawAzimuth)
                            applyZRotation(remappedMag, declinationRad)

                            trySend(
                                DeviceOrientation(
                                    azimuthRad = headingFromMatrix(remappedMag),
                                    pitchRad = pitchFromMatrix(remappedMag),
                                    rollRad = rollFromMatrix(remappedMag),
                                    rotationMatrix = remappedMag.copyOf(),
                                )
                            )
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                if (sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                    magAccuracy = accuracy
                }
            }
        }

        if (gameSensor != null) {
            sensorManager.registerListener(listener, gameSensor, SensorManager.SENSOR_DELAY_GAME)
            log(TAG) { "Registered GAME_ROTATION_VECTOR sensor" }
        }
        if (rotSensor != null) {
            val rate = if (useFusion) SensorManager.SENSOR_DELAY_UI else SensorManager.SENSOR_DELAY_GAME
            sensorManager.registerListener(listener, rotSensor, rate)
            log(TAG) { "Registered ROTATION_VECTOR sensor (rate=${if (useFusion) "UI" else "GAME"})" }
        }

        awaitClose {
            log(TAG) { "Sensor listeners unregistered" }
            sensorManager.unregisterListener(listener)
        }
    }.conflate().flowOn(dispatcherProvider.Default)

    companion object {
        private val TAG = logTag("AR", "DeviceOrientationProvider")
        private const val FALLBACK_ALPHA = 0.15f
        private val VERTICAL_THRESHOLD = cos(Math.toRadians(15.0)).toFloat()
    }
}
