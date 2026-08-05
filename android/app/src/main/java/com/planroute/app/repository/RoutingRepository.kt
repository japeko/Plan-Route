package com.planroute.app.repository

import com.planroute.app.model.RouteOption
import com.planroute.app.network.OsrmStep
import com.planroute.app.network.osrmApi
import java.io.IOException
import kotlin.math.roundToInt
import org.osmdroid.util.GeoPoint
import retrofit2.HttpException

/** A route alternative from OSRM: display info, the polyline to draw on the map, and its turn-by-turn steps. */
data class PlannedRoute(
    val option: RouteOption,
    val geometry: List<GeoPoint>,
    val steps: List<RouteStep> = emptyList(),
)

/**
 * One turn-by-turn step, kept as OSRM's raw maneuver fields rather than a
 * pre-formatted string, so callers can both localize it for spoken/banner
 * guidance ([com.planroute.app.voice.localizedInstruction]) and render a
 * plain debug label (the "Show directions" list).
 */
data class RouteStep(
    /** "depart" | "arrive" | "turn" | "merge" | "roundabout" | "fork" | "end of road" | ... */
    val maneuverType: String,
    /** "left" | "right" | "straight" | "slight left" | "sharp right" | "uturn" | ... — often absent. */
    val maneuverModifier: String?,
    /** Often blank for unnamed roads/ramps. */
    val streetName: String,
    val distanceMeters: Int,
    /** Where this maneuver happens — used to match the step against the vehicle's live position. */
    val location: GeoPoint,
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
                    steps = route.legs.flatMap { leg -> leg.steps.map { it.toRouteStep() } },
                )
            }
        } catch (e: IOException) {
            emptyList()
        } catch (e: HttpException) {
            emptyList()
        }
    }
}

private fun OsrmStep.toRouteStep(): RouteStep = RouteStep(
    maneuverType = maneuver.type,
    maneuverModifier = maneuver.modifier,
    streetName = name,
    distanceMeters = distance.roundToInt(),
    location = GeoPoint(
        maneuver.location.getOrElse(1) { 0.0 },
        maneuver.location.getOrElse(0) { 0.0 },
    ),
)
