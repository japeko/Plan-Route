package com.planroute.app.network

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** https://router.project-osrm.org — the public OSRM demo instance, driving profile. */
interface OsrmApi {
    @GET("route/v1/driving/{coordinates}")
    suspend fun route(
        @Path("coordinates") coordinates: String,
        @Query("alternatives") alternatives: Boolean = true,
        @Query("geometries") geometries: String = "geojson",
        @Query("overview") overview: String = "full",
    ): OsrmRouteResponse
}

@Serializable
data class OsrmRouteResponse(
    val code: String,
    val routes: List<OsrmRoute> = emptyList(),
)

@Serializable
data class OsrmRoute(
    /** Meters. */
    val distance: Double,
    /** Seconds. */
    val duration: Double,
    val geometry: OsrmGeometry,
)

@Serializable
data class OsrmGeometry(
    val type: String,
    /** [lon, lat] pairs, per GeoJSON convention. */
    val coordinates: List<List<Double>> = emptyList(),
)
