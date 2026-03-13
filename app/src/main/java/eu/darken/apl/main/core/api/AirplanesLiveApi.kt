package eu.darken.apl.main.core.api

import android.location.Location
import eu.darken.apl.main.core.aircraft.AircraftHex
import eu.darken.apl.main.core.aircraft.Airframe
import eu.darken.apl.main.core.aircraft.Callsign
import eu.darken.apl.main.core.aircraft.Registration
import eu.darken.apl.main.core.aircraft.SquawkCode
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path
import java.time.Instant

interface AirplanesLiveApi {

    interface BaseResponse {
        val message: String
        val now: Long
        val ctime: Long
        val ptime: Int
    }

    @Serializable
    data class HexesResponse(
        @SerialName("ac") val ac: List<@Contextual Aircraft>,
        @SerialName("total") val total: Int,
        @SerialName("now") override val now: Long,
        @SerialName("msg") override val message: String,
        @SerialName("ctime") override val ctime: Long,
        @SerialName("ptime") override val ptime: Int
    ) : BaseResponse

    @GET("hex/{hexes}")
    suspend fun getAircraftByHex(@Path("hexes", encoded = true) hexes: String): HexesResponse

    @Serializable
    data class SquawksResponse(
        @SerialName("ac") val ac: List<@Contextual Aircraft>,
        @SerialName("total") val total: Int,
        @SerialName("msg") override val message: String,
        @SerialName("now") override val now: Long,
        @SerialName("ctime") override val ctime: Long,
        @SerialName("ptime") override val ptime: Int
    ) : BaseResponse

    @GET("squawk/{codes}")
    suspend fun getAircraftBySquawk(@Path("codes", encoded = true) codes: String): SquawksResponse

    @Serializable
    data class CallsignsResponse(
        @SerialName("ac") val ac: List<@Contextual Aircraft>,
        @SerialName("total") val total: Int,
        @SerialName("msg") override val message: String,
        @SerialName("now") override val now: Long,
        @SerialName("ctime") override val ctime: Long,
        @SerialName("ptime") override val ptime: Int
    ) : BaseResponse

    @GET("callsign/{signs}")
    suspend fun getAircraftByCallsign(@Path("signs", encoded = true) codes: String): CallsignsResponse

    @Serializable
    data class RegistrationsResponse(
        @SerialName("ac") val ac: List<@Contextual Aircraft>,
        @SerialName("total") val total: Int,
        @SerialName("msg") override val message: String,
        @SerialName("now") override val now: Long,
        @SerialName("ctime") override val ctime: Long,
        @SerialName("ptime") override val ptime: Int
    ) : BaseResponse

    @GET("reg/{regs}")
    suspend fun getAircraftByRegistration(@Path("regs", encoded = true) codes: String): RegistrationsResponse

    @Serializable
    data class AirframesResponse(
        @SerialName("ac") val ac: List<@Contextual Aircraft>,
        @SerialName("total") val total: Int,
        @SerialName("msg") override val message: String,
        @SerialName("now") override val now: Long,
        @SerialName("ctime") override val ctime: Long,
        @SerialName("ptime") override val ptime: Int
    ) : BaseResponse

    @GET("type/{types}")
    suspend fun getAircraftByAirframe(@Path("types", encoded = true) types: String): AirframesResponse

    @Serializable
    data class MilitaryResponse(
        @SerialName("ac") val ac: List<@Contextual Aircraft>,
        @SerialName("total") val total: Int,
        @SerialName("msg") override val message: String,
        @SerialName("now") override val now: Long,
        @SerialName("ctime") override val ctime: Long,
        @SerialName("ptime") override val ptime: Int
    ) : BaseResponse

    @GET("mil")
    suspend fun getMilitary(): MilitaryResponse

    @Serializable
    data class LADDResponse(
        @SerialName("ac") val ac: List<@Contextual Aircraft>,
        @SerialName("total") val total: Int,
        @SerialName("msg") override val message: String,
        @SerialName("now") override val now: Long,
        @SerialName("ctime") override val ctime: Long,
        @SerialName("ptime") override val ptime: Int
    ) : BaseResponse

    @GET("ladd")
    suspend fun getLADD(): LADDResponse

    @Serializable
    data class PIAResponse(
        @SerialName("ac") val ac: List<@Contextual Aircraft>,
        @SerialName("total") val total: Int,
        @SerialName("msg") override val message: String,
        @SerialName("now") override val now: Long,
        @SerialName("ctime") override val ctime: Long,
        @SerialName("ptime") override val ptime: Int
    ) : BaseResponse

    @GET("pia")
    suspend fun getPIA(): PIAResponse

    @GET("point/{lat}/{lon}/{radius}")
    suspend fun getAircraftsByLocation(
        @Path("lat") latitude: Double,
        @Path("lon") longitude: Double,
        @Path("radius") radius: Int
    ): AirframesResponse

    @Serializable
    data class Aircraft(
        @SerialName("hex") val rawHex: AircraftHex,
        @SerialName("type") override val messageType: String,
        @SerialName("dbFlags") override val dbFlags: Int?,
        @SerialName("r") override val registration: Registration?,
        @SerialName("flight") override val callsign: Callsign?,

        @SerialName("ownOp") override val operator: String?,
        @SerialName("t") override val airframe: Airframe?,
        @SerialName("desc") override val description: String?,

        @SerialName("squawk") override val squawk: SquawkCode?,
        @SerialName("emergency") override val emergency: String?,

        @SerialName("oat") val oat: Int?, // outer/static air temperature (C)
        @SerialName("tat") val tat: Int?, // total air temperature (C)
        @SerialName("alt_baro") override val altitude: String?, // Altitude in feet or "ground"
        @SerialName("baro_rate") val rateBaro: Int?,
        @SerialName("geom_rate") val rateGeometric: Int?,
        @SerialName("gs") override val groundSpeed: Float?, // ground speed in knots
        @SerialName("ias") override val indicatedAirSpeed: Int?, // indicated air speed in knots
        @SerialName("mag_heading") val headingMagnetic: Double?, // Heading, degrees clockwise from magnetic north
        @SerialName("true_heading") val headingTrue: Double?, // Heading, degrees clockwise from true north
        @SerialName("lat") val latitude: String?,
        @SerialName("lon") val longitude: String?,
        @SerialName("track") val track: Float?, // true track over ground in degrees (0-359)
        @SerialName("rr_lat") val roughLat: Double?, // If no ADS-B or MLAT position available, a rough estimated position for the aircraft based on the receiver's estimated coordinates.
        @SerialName("rr_lon") val roughLon: Double?, // If no ADS-B or MLAT position available, a rough estimated position for the aircraft based on the receiver's estimated coordinates.
        @SerialName("lastPosition") val lastPosition: LastPosition?,

        @SerialName("version") val version: Int?,

        @SerialName("messages") override val messages: Int,
        @SerialName("rssi") override val rssi: Double,
        @SerialName("seen") val seenSecondsAgo: Double
    ) : eu.darken.apl.main.core.aircraft.Aircraft {

        override val hex: AircraftHex
            get() = rawHex.uppercase()

        @Contextual
        override val seenAt: Instant
            get() = Instant.now().minusSeconds(seenSecondsAgo.toLong())

        override val outsideTemp: Int?
            get() = oat ?: tat

        override val altitudeRate: Int?
            get() = rateBaro ?: rateGeometric

        override val trackheading: Double?
            get() = headingMagnetic ?: headingTrue

        override val groundTrack: Float?
            get() = track

        override val location: Location?
            get() {
                val convLat = latitude?.toDouble() ?: roughLat ?: return null
                val convLong = longitude?.toDouble() ?: roughLon ?: return null

                return Location("apl").apply {
                    latitude = convLat
                    longitude = convLong
                }
            }

        @Serializable
        data class LastPosition(
            @SerialName("lat") val lat: Double,
            @SerialName("lon") val lon: Double,
            @SerialName("nic") val nic: Int,
            @SerialName("rc") val rc: Int,
            @SerialName("seen_pos") val lastSeen: Double
        )

        override fun toString(): String {
            return "Aircraft($hex, $registration, $airframe)"
        }
    }

}
