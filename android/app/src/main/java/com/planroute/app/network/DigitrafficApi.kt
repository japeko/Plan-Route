package com.planroute.app.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import retrofit2.http.GET
import retrofit2.http.Query

/** https://tie.digitraffic.fi — Fintraffic's open road data; this is the official road-work feed. */
interface DigitrafficApi {
    @GET("api/traffic-message/v2/roadworks")
    suspend fun roadworks(
        @Query("xMin") xMin: Double,
        @Query("yMin") yMin: Double,
        @Query("xMax") xMax: Double,
        @Query("yMax") yMax: Double,
    ): RoadworkFeatureCollection
}

@Serializable
data class RoadworkFeatureCollection(
    val type: String,
    val features: List<RoadworkFeature> = emptyList(),
)

@Serializable
data class RoadworkFeature(
    /** GeoJSON geometry — shape varies (Point/LineString/MultiLineString/Polygon); see [firstCoordinate]. */
    val geometry: JsonElement,
    val properties: RoadworkProperties,
)

@Serializable
data class RoadworkProperties(
    val situationId: String,
    val announcements: List<RoadworkAnnouncement> = emptyList(),
)

@Serializable
data class RoadworkAnnouncement(
    val language: String? = null,
    val title: String? = null,
)

/**
 * Digitraffic geometries nest coordinates to different depths depending on
 * the GeoJSON type (Point vs. LineString vs. MultiLineString/Polygon). For
 * placing a single map pin we only need one representative point, so this
 * descends into "coordinates" and keeps taking the first element until it
 * finds a leaf `[lon, lat]` pair.
 */
fun JsonElement.firstCoordinate(): Pair<Double, Double>? {
    var current: JsonElement = (this as? JsonObject)?.get("coordinates") ?: this
    while (current is JsonArray) {
        val first = current.firstOrNull() ?: return null
        if (first is JsonPrimitive) {
            val lon = current.getOrNull(0)?.let { (it as? JsonPrimitive)?.doubleOrNull }
            val lat = current.getOrNull(1)?.let { (it as? JsonPrimitive)?.doubleOrNull }
            return if (lon != null && lat != null) lon to lat else null
        }
        current = first
    }
    return null
}
