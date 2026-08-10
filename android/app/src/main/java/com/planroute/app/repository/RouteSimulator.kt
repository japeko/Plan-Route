package com.planroute.app.repository

import android.location.Location
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import org.osmdroid.util.GeoPoint

/**
 * Fabricates a [Flow] of [Location] fixes that walks a [PlannedRoute]'s
 * geometry over simulated time — for exercising navigation (voice
 * announcements, the banner, the vehicle marker, the speed readout)
 * without actually driving or wiring up OS-level mock-location on a
 * physical device. Speed at each point comes from the OSRM step covering
 * that point (distanceMeters/durationSeconds) for faster/highway stretches,
 * so the simulated car still speeds up there the way the real road's
 * implied limit would; anything reading as city-paced (at or under
 * CitySpeedCutoffMps) is flattened to a constant CitySpeedMps instead of
 * following OSRM's per-step figure exactly. Debug-only, same spirit as the
 * "Show directions" list.
 */
object RouteSimulator {
    private const val TickMillis = 500L
    private const val TickSeconds = TickMillis / 1000.0

    /** Flat speed used for any step whose OSRM-implied pace reads as city driving (at or under CitySpeedCutoffMps) — also the fallback when a step has no usable duration. */
    private const val CitySpeedMps = 40.0 / 3.6
    private const val CitySpeedCutoffMps = 60.0 / 3.6
    private const val MinSpeedMps = 3.0
    private const val MaxSpeedMps = 40.0

    // Where simulateOffRoute deliberately drifts sideways off the planned
    // route — a fixed stretch (fraction of total route distance) rather
    // than the whole trip, so the rest of the drive still exercises normal
    // on-route navigation before and after.
    private const val OffRouteStartFraction = 0.25
    private const val OffRouteEndFraction = 0.4
    private const val OffRouteOffsetMeters = 250.0

    /**
     * [simulateOffRoute], when true, drifts the emitted position sideways
     * off the route's own path for a stretch in the middle of the drive —
     * for exercising MainActivity's off-route detection/detour-back
     * feature without needing OS-level mock-location or an actual wrong
     * turn. The route itself and the driving pace are otherwise unchanged.
     */
    fun simulateDrive(route: PlannedRoute, simulateOffRoute: Boolean = false): Flow<Location> = flow {
        val geometry = route.geometry
        if (geometry.size < 2) return@flow

        // Cumulative distance (meters) at each geometry point, so a
        // "distance traveled" cursor can be turned back into a position.
        val cumulative = DoubleArray(geometry.size)
        for (i in 1 until geometry.size) {
            cumulative[i] = cumulative[i - 1] + geometry[i - 1].distanceToAsDouble(geometry[i])
        }
        val totalDistance = cumulative.last()
        if (totalDistance <= 0.0) return@flow

        // Each step's maneuver location is matched to the nearest geometry
        // point, giving cumulative-distance thresholds along the route —
        // the step whose threshold is next reached governs the implied
        // speed (distanceMeters/durationSeconds) for the stretch up to it.
        val stepThresholds = route.steps
            .map { step ->
                val nearestIndex = geometry.indices.minByOrNull { step.location.distanceToAsDouble(geometry[it]) } ?: 0
                cumulative[nearestIndex] to step
            }
            .sortedBy { it.first }

        fun speedMpsAt(distanceTraveled: Double): Double {
            val step = stepThresholds.firstOrNull { distanceTraveled <= it.first }?.second
                ?: stepThresholds.lastOrNull()?.second
            if (step == null || step.durationSeconds <= 0) return CitySpeedMps
            val impliedSpeedMps = step.distanceMeters.toDouble() / step.durationSeconds
            if (impliedSpeedMps <= CitySpeedCutoffMps) return CitySpeedMps
            return impliedSpeedMps.coerceIn(MinSpeedMps, MaxSpeedMps)
        }

        fun positionAt(distanceTraveled: Double): GeoPoint {
            val clamped = distanceTraveled.coerceIn(0.0, totalDistance)
            val index = cumulative.indexOfLast { it <= clamped }.coerceIn(0, geometry.size - 2)
            val segmentLength = cumulative[index + 1] - cumulative[index]
            if (segmentLength <= 0.0) return geometry[index]
            val fraction = ((clamped - cumulative[index]) / segmentLength).coerceIn(0.0, 1.0)
            val from = geometry[index]
            val to = geometry[index + 1]
            return GeoPoint(
                from.latitude + (to.latitude - from.latitude) * fraction,
                from.longitude + (to.longitude - from.longitude) * fraction,
            )
        }

        val offRouteStart = totalDistance * OffRouteStartFraction
        val offRouteEnd = totalDistance * OffRouteEndFraction

        var distanceTraveled = 0.0
        var previousPosition = geometry.first()
        while (distanceTraveled < totalDistance) {
            val speedMps = speedMpsAt(distanceTraveled)
            distanceTraveled = (distanceTraveled + speedMps * TickSeconds).coerceAtMost(totalDistance)
            val onPathPosition = positionAt(distanceTraveled)
            val headingDegrees = bearingBetween(previousPosition, onPathPosition)
            val position = if (simulateOffRoute && distanceTraveled in offRouteStart..offRouteEnd) {
                offsetPosition(onPathPosition, headingDegrees + 90f, OffRouteOffsetMeters)
            } else {
                onPathPosition
            }
            emit(
                Location("simulated").apply {
                    latitude = position.latitude
                    longitude = position.longitude
                    speed = speedMps.toFloat()
                    bearing = headingDegrees
                    time = System.currentTimeMillis()
                },
            )
            previousPosition = onPathPosition
            delay(TickMillis)
        }
    }
}

private const val EarthRadiusMeters = 6_371_000.0

/** The point [distanceMeters] away from [start] in direction [bearingDegrees] — standard spherical-Earth destination-point formula. */
private fun offsetPosition(start: GeoPoint, bearingDegrees: Float, distanceMeters: Double): GeoPoint {
    val angularDistance = distanceMeters / EarthRadiusMeters
    val bearingRad = Math.toRadians(bearingDegrees.toDouble())
    val lat1 = Math.toRadians(start.latitude)
    val lon1 = Math.toRadians(start.longitude)

    val lat2 = asin(sin(lat1) * cos(angularDistance) + cos(lat1) * sin(angularDistance) * cos(bearingRad))
    val lon2 = lon1 + atan2(
        sin(bearingRad) * sin(angularDistance) * cos(lat1),
        cos(angularDistance) - sin(lat1) * sin(lat2),
    )
    return GeoPoint(Math.toDegrees(lat2), Math.toDegrees(lon2))
}

private fun bearingBetween(from: GeoPoint, to: GeoPoint): Float {
    val lat1 = Math.toRadians(from.latitude)
    val lat2 = Math.toRadians(to.latitude)
    val deltaLon = Math.toRadians(to.longitude - from.longitude)
    val y = sin(deltaLon) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLon)
    return ((Math.toDegrees(atan2(y, x)) + 360) % 360).toFloat()
}
