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
        @Query("steps") steps: Boolean = true,
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
    val legs: List<OsrmLeg> = emptyList(),
)

@Serializable
data class OsrmGeometry(
    val type: String,
    /** [lon, lat] pairs, per GeoJSON convention. */
    val coordinates: List<List<Double>> = emptyList(),
)

/** One leg per waypoint-to-waypoint segment (start→via, via→via, via→end, ...). */
@Serializable
data class OsrmLeg(
    val steps: List<OsrmStep> = emptyList(),
)

@Serializable
data class OsrmStep(
    /** Meters. */
    val distance: Double,
    /** Seconds. */
    val duration: Double,
    /** Street name — often blank for unnamed roads/ramps. */
    val name: String = "",
    val maneuver: OsrmManeuver,
)

@Serializable
data class OsrmManeuver(
    /** "depart" | "arrive" | "turn" | "merge" | "roundabout" | "fork" | "end of road" | ... — see OSRM's step maneuver docs. */
    val type: String,
    /** "left" | "right" | "straight" | "slight left" | "sharp right" | "uturn" | ... — absent for types like "depart"/"arrive". */
    val modifier: String? = null,
)
