package com.planroute.app.repository

import android.util.Log
import com.planroute.app.model.GasAmenity
import com.planroute.app.model.PoiMarker
import com.planroute.app.model.PoiType
import com.planroute.app.network.GeoLineStringDto
import com.planroute.app.network.PoiAlongRouteRequest
import com.planroute.app.network.PoiDto
import com.planroute.app.network.poiApi
import java.io.IOException
import org.osmdroid.util.GeoPoint
import retrofit2.HttpException

/** Server POI marker ids live in their own namespace, away from road-work (negative) and user-report (small positive) ids. */
private const val ServerPoiIdOffset = 1_000_000

private const val TAG = "PlanRoutePoi"

/** What the user currently wants along the route — mirrors the filter chips/sliders in the planner sheet. */
data class PoiSearchSettings(
    val showGasStations: Boolean,
    val gasMaxDistanceMeters: Double,
    val gasAmenities: Set<GasAmenity>,
    /** Shares gasMaxDistanceMeters as its search radius — the server's alongRouteRequestSchema has one radiusMeters field covering both gas stations and standalone restaurants, not a separate one per type. */
    val showRestaurants: Boolean,
    val showCamping: Boolean,
    val campingMaxDistanceMeters: Double,
    val showAccommodation: Boolean,
    val accommodationMaxDistanceMeters: Double,
)

/**
 * Wraps this project's own POI server (packages/server's
 * `/api/pois/along-route`) — gas stations, restaurants, camping areas, and
 * hotels/hostels along a planned route. Address configured locally via
 * .env; see [com.planroute.app.network.PoiApi] for the exact request/
 * response shape this mirrors.
 */
object PoiRepository {
    suspend fun searchAlongRoute(routeGeometry: List<GeoPoint>, settings: PoiSearchSettings): List<PoiMarker> {
        if (routeGeometry.size < 2) return emptyList()
        if (!settings.showGasStations && !settings.showRestaurants && !settings.showCamping && !settings.showAccommodation) {
            return emptyList()
        }

        val request = PoiAlongRouteRequest(
            route = GeoLineStringDto(coordinates = routeGeometry.map { listOf(it.longitude, it.latitude) }),
            radiusMeters = settings.gasMaxDistanceMeters,
            showRestaurants = settings.showRestaurants,
            showGasStations = settings.showGasStations,
            fuelTypes = settings.gasAmenities.mapNotNull {
                when (it) {
                    GasAmenity.GASOLINE -> "gasoline"
                    GasAmenity.ELECTRIC -> "electric"
                    GasAmenity.RESTAURANT -> null
                }
            },
            onlyWithRestaurant = GasAmenity.RESTAURANT in settings.gasAmenities,
            showCamping = settings.showCamping,
            campingRadiusMeters = settings.campingMaxDistanceMeters,
            showAccommodation = settings.showAccommodation,
            accommodationRadiusMeters = settings.accommodationMaxDistanceMeters,
        )

        return try {
            poiApi.searchAlongRoute(request).mapIndexedNotNull { index, dto -> dto.toMarker(index) }
        } catch (e: IOException) {
            Log.e(TAG, "searchAlongRoute: network failure", e)
            emptyList()
        } catch (e: HttpException) {
            Log.e(TAG, "searchAlongRoute: HTTP ${e.code()} ${e.message()} body=${e.response()?.errorBody()?.string()}", e)
            emptyList()
        }
    }
}

private fun PoiDto.toMarker(index: Int): PoiMarker? {
    val (poiType, description) = when (type) {
        "gas_station" -> PoiType.GAS to listOfNotNull(
            "Gasoline".takeIf { hasGasoline == true },
            "Electric charging".takeIf { hasElectricCharging == true },
            "Restaurant".takeIf { hasRestaurant == true },
        ).joinToString(", ").ifBlank { "Gas station" }
        "camping" -> PoiType.CAMPING to listOfNotNull(
            "Tent sites".takeIf { hasTentSites == true },
            "Caravan sites".takeIf { hasCaravanSites == true },
        ).joinToString(", ").ifBlank { "Camping area" }
        "accommodation" -> PoiType.HOTEL to (category?.replaceFirstChar(Char::uppercase) ?: "Hotel / hostel")
        "restaurant" -> PoiType.RESTAURANT to "Restaurant"
        else -> return null
    }
    val (lon, lat) = location.coordinates
    return PoiMarker(
        id = ServerPoiIdOffset + index,
        type = poiType,
        title = name,
        description = description,
        position = GeoPoint(lat, lon),
    )
}
