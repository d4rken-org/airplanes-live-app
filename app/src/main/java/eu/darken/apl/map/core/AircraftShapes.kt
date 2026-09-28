package eu.darken.apl.map.core

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.apl.common.coroutine.DispatcherProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import javax.inject.Inject
import javax.inject.Singleton

/** Aircraft silhouettes from tar1090, and which one an aircraft is drawn with. */
@Serializable
data class AircraftShapes(
    val shapes: Map<String, Shape>,
    val designators: Map<String, Ref>,
    val descriptions: Map<String, Ref>,
    val categories: Map<String, Ref>,
    /** ICAO type designator to its ICAO description and wake category, e.g. C172 to [L1P, L]. */
    val types: Map<String, List<String>>,
) {

    /** An SVG outline, [w] and [h] in tar1090's pixels, where its generic airliner is 32 wide. */
    @Serializable
    data class Shape(
        val w: Float,
        val h: Float,
        val viewBox: List<Float>,
        val paths: List<String>,
        val strokeScale: Float = 1f,
        val rotates: Boolean = true,
        val stretch: Boolean = false,
        /** SVG affine transform a, b, c, d, e, f. */
        val matrix: List<Float>? = null,
    )

    @Serializable
    data class Ref(val shape: String, val scale: Float)

    /**
     * tar1090's order: an icon for the exact type, then for its ICAO description with and without
     * the wake category, then for the kind of aircraft alone, then for the broadcast category.
     * Null when nothing matches.
     */
    fun refFor(type: String?, category: String?): Ref? {
        type?.let { designators[it] }?.let { return it }

        val (description, wake) = type?.let { types[it] }.orEmpty().let { it.getOrNull(0) to it.getOrNull(1) }
        if (description != null && description.length == 3) {
            if (description == "L1P" && category == "B4") return ULTRALIGHT
            if (wake != null && wake.length == 1) {
                if (description == "L2J" && wake == "M" && category == "A2") return BUSINESS_JET
                descriptions["$description-$wake"]?.let { return it }
            }
            descriptions[description]?.let { return it }
            descriptions[description.take(1)]?.let { return it.copy(scale = 1f) }
        }

        return category?.let { categories[it] }
    }

    companion object {
        private val ULTRALIGHT = Ref("cessna", 0.92f)
        private val BUSINESS_JET = Ref("jet_swept", 1f)
    }
}

@Singleton
class AircraftShapesRepo @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
    private val dispatcherProvider: DispatcherProvider,
) {
    private val lock = Mutex()
    private var cached: AircraftShapes? = null

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun shapes(): AircraftShapes = lock.withLock {
        cached ?: withContext(dispatcherProvider.IO) {
            context.assets.open(ASSET).use { json.decodeFromStream<AircraftShapes>(it) }
        }.also { cached = it }
    }

    companion object {
        const val ASSET = "map/aircraft_shapes.json"
    }
}
