package com.planroute.app.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/** https://nominatim.openstreetmap.org — free-text address search and reverse geocoding. */
interface NominatimApi {
    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("format") format: String = "jsonv2",
        @Query("limit") limit: Int = 5,
        @Query("countrycodes") countryCodes: String? = null,
    ): List<NominatimPlace>

    @GET("reverse")
    suspend fun reverse(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("format") format: String = "jsonv2",
    ): NominatimPlace
}

@Serializable
data class NominatimPlace(
    @SerialName("place_id") val placeId: Long? = null,
    val lat: String,
    val lon: String,
    @SerialName("display_name") val displayName: String,
)
