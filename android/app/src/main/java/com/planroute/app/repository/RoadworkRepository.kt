package com.planroute.app.repository

import com.planroute.app.model.PoiMarker
import com.planroute.app.model.PoiType
import com.planroute.app.network.digitrafficApi
import com.planroute.app.network.firstCoordinate
import java.io.IOException
import kotlin.math.cos
import kotlin.math.hypot
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import retrofit2.HttpException

/** How far (meters) a road-work point can sit from the route line and still count as "on the route". */
private const val RouteCorridorMeters = 500.0
private const val EarthRadiusMeters = 6371000.0

/**
 * Wraps Digitraffic's official road-work feed — called directly by the
 * client, not proxied through packages/server. Only fetched for the
 * currently selected route's corridor (not the whole viewport, and not
 * shown until the caller opts in), since most of a wide map viewport isn't
 * relevant to a driver who already has a route planned. Marker ids use a
 * negative namespace so they never collide with the locally-assigned ids
 * for the demo POI / user-submitted reports (see MainActivity's
 * `nextMarkerId`).
 */
object RoadworkRepository {
    suspend fun roadworksOnRoute(
        routeGeometry: List<GeoPoint>,
        corridorMeters: Double = RouteCorridorMeters,
    ): List<PoiMarker> {
        if (routeGeometry.size < 2) return emptyList()
        return roadworksIn(boundingBoxOf(routeGeometry))
            .filter { distanceToPolylineMeters(it.position, routeGeometry) <= corridorMeters }
    }

    private suspend fun roadworksIn(bbox: BoundingBox): List<PoiMarker> {
        return try {
            digitrafficApi.roadworks(
                xMin = bbox.lonWest,
                yMin = bbox.latSouth,
                xMax = bbox.lonEast,
                yMax = bbox.latNorth,
            ).features.mapIndexedNotNull { index, feature ->
                val (lon, lat) = feature.geometry.firstCoordinate() ?: return@mapIndexedNotNull null
                val announcement = feature.properties.announcements.firstOrNull { it.language == "en" }
                    ?: feature.properties.announcements.firstOrNull()
                PoiMarker(
                    id = -(index + 1),
                    type = PoiType.ROAD_WORK,
                    title = announcement?.title ?: "Road work",
                    description = "Fintraffic · official report",
                    position = GeoPoint(lat, lon),
                    isUserReport = false,
                )
            }
        } catch (e: IOException) {
            emptyList()
        } catch (e: HttpException) {
            emptyList()
        }
    }
}

private fun boundingBoxOf(points: List<GeoPoint>): BoundingBox {
    val lats = points.map { it.latitude }
    val lons = points.map { it.longitude }
    return BoundingBox(lats.max(), lons.max(), lats.min(), lons.min())
}

/** Flat-earth (equirectangular) approximation — plenty accurate at route-corridor scales. */
private fun GeoPoint.toMeters(referenceLatitude: Double): Pair<Double, Double> {
    val x = Math.toRadians(longitude) * EarthRadiusMeters * cos(Math.toRadians(referenceLatitude))
    val y = Math.toRadians(latitude) * EarthRadiusMeters
    return x to y
}

private fun distanceToSegmentMeters(point: GeoPoint, a: GeoPoint, b: GeoPoint): Double {
    val (px, py) = point.toMeters(a.latitude)
    val (ax, ay) = a.toMeters(a.latitude)
    val (bx, by) = b.toMeters(a.latitude)
    val dx = bx - ax
    val dy = by - ay
    val lengthSquared = dx * dx + dy * dy
    if (lengthSquared == 0.0) return hypot(px - ax, py - ay)
    val t = (((px - ax) * dx + (py - ay) * dy) / lengthSquared).coerceIn(0.0, 1.0)
    return hypot(px - (ax + t * dx), py - (ay + t * dy))
}

private fun distanceToPolylineMeters(point: GeoPoint, polyline: List<GeoPoint>): Double =
    (0 until polyline.size - 1).minOf { i -> distanceToSegmentMeters(point, polyline[i], polyline[i + 1]) }
