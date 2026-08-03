package com.planroute.app.repository

import com.planroute.app.model.RouteOption
import com.planroute.app.network.osrmApi
import java.io.IOException
import kotlin.math.roundToInt
import org.osmdroid.util.GeoPoint
import retrofit2.HttpException

/** A route alternative from OSRM: display info plus the polyline to draw on the map. */
data class PlannedRoute(
    val option: RouteOption,
    val geometry: List<GeoPoint>,
)

/** Wraps the public OSRM demo instance — called directly by the client, not proxied through packages/server. */
object RoutingRepository {
    suspend fun route(stops: List<GeoPoint>): List<PlannedRoute> {
        if (stops.size < 2) return emptyList()
        val coordinates = stops.joinToString(";") { "${it.longitude},${it.latitude}" }
        return try {
            osrmApi.route(coordinates = coordinates).routes.mapIndexed { index, route ->
                PlannedRoute(
                    option = RouteOption(
                        id = index,
                        label = "Route ${index + 1}",
                        distanceKm = (route.distance / 1000).roundToInt(),
                        durationMinutes = (route.duration / 60).roundToInt(),
                    ),
                    geometry = route.geometry.coordinates.map { GeoPoint(it[1], it[0]) },
                )
            }
        } catch (e: IOException) {
            emptyList()
        } catch (e: HttpException) {
            emptyList()
        }
    }
}
