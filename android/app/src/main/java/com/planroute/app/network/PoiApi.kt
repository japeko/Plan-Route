package com.planroute.app.network

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * This project's own POI server — packages/server's `/api/pois` routes
 * (see poi.routes.ts / poi.validator.ts / poi.service.ts in the repo
 * root), address configured via [com.planroute.app.BuildConfig.SERVER_BASE_URL]
 * (see .env / .env.example). This mirrors that server's real request/
 * response contract exactly, not an invented one.
 */
interface PoiApi {
    @POST("api/pois/along-route")
    suspend fun searchAlongRoute(@Body request: PoiAlongRouteRequest): List<PoiDto>
}

@Serializable
data class GeoLineStringDto(
    val type: String = "LineString",
    /** `[lon, lat]` pairs, per GeoJSON. */
    val coordinates: List<List<Double>>,
)

@Serializable
data class GeoPointDto(
    val type: String = "Point",
    /** `[lon, lat]`, per GeoJSON. */
    val coordinates: List<Double>,
)

/**
 * Matches packages/server's `alongRouteRequestSchema` exactly. Two
 * details worth knowing:
 *  - `radiusMeters` is shared by gas stations *and* restaurants server-side;
 *    since this client never asks for standalone restaurants, it's used as
 *    the gas-station radius and [showRestaurants] is always false.
 *  - the radius fields are required (server validates `.positive()`)
 *    regardless of whether the matching `show*` flag is true — the server
 *    just ignores a radius whose category isn't requested.
 */
@Serializable
data class PoiAlongRouteRequest(
    val route: GeoLineStringDto,
    val radiusMeters: Double,
    val showRestaurants: Boolean,
    val showGasStations: Boolean,
    /** "gasoline" | "electric" — server drops gas stations entirely if this is empty. */
    val fuelTypes: List<String>,
    val onlyWithRestaurant: Boolean,
    val showCamping: Boolean,
    val campingRadiusMeters: Double,
    val showAccommodation: Boolean,
    val accommodationRadiusMeters: Double,
)

/** One of packages/server's PointOfInterest union members — fields beyond [type] vary by it. */
@Serializable
data class PoiDto(
    val id: String,
    val name: String,
    /** "gas_station" | "restaurant" | "camping" | "accommodation" */
    val type: String,
    val location: GeoPointDto,
    val address: String? = null,
    val hasGasoline: Boolean? = null,
    val hasElectricCharging: Boolean? = null,
    val hasRestaurant: Boolean? = null,
    val hasTentSites: Boolean? = null,
    val hasCaravanSites: Boolean? = null,
    /** "hotel" | "hostel" — only for type == "accommodation". */
    val category: String? = null,
)
