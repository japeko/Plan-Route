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
    /** OSRM's estimate for this step alone — distanceMeters/durationSeconds approximates the road's speed limit, used by RouteSimulator. */
    val durationSeconds: Int,
    /** Where this maneuver happens — used to match the step against the vehicle's live position. */
    val location: GeoPoint,
)

/** Wraps the public OSRM demo instance — called directly by the client, not proxied through packages/server. */
object RoutingRepository {
    /**
     * [destinationLabel] is the address text the user actually typed/searched
     * for (e.g. "Kullervonkatu 9, Tampere") — OSRM's own step names never
     * carry a house number, only the street, so the final "arrive" step's
     * streetName is overridden with this (just the part before the first
     * comma) when available. Without it, both the spoken/banner arrival
     * instruction and the debug directions list end at a bare street name
     * even when the user asked for a specific address on it.
     */
    suspend fun route(stops: List<GeoPoint>, destinationLabel: String? = null): List<PlannedRoute> {
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
                    steps = route.legs.flatMap { leg -> leg.steps.map { it.toRouteStep() } }
                        .withAccurateDestination(destinationLabel),
                )
            }
        } catch (e: IOException) {
            emptyList()
        } catch (e: HttpException) {
            emptyList()
        }
    }
}

private fun List<RouteStep>.withAccurateDestination(destinationLabel: String?): List<RouteStep> {
    val label = destinationLabel?.substringBefore(",")?.trim()
    if (label.isNullOrBlank() || isEmpty()) return this
    return mapIndexed { index, step -> if (index == lastIndex) step.copy(streetName = label) else step }
}

private fun OsrmStep.toRouteStep(): RouteStep = RouteStep(
    maneuverType = maneuver.type,
    maneuverModifier = maneuver.modifier,
    streetName = name,
    distanceMeters = distance.roundToInt(),
    durationSeconds = duration.roundToInt(),
    location = GeoPoint(
        maneuver.location.getOrElse(1) { 0.0 },
        maneuver.location.getOrElse(0) { 0.0 },
    ),
)
