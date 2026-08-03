package com.planroute.app.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.osmdroid.util.GeoPoint

/** Which end of the route a stop represents. */
enum class StopKind { START, VIA, END }

/**
 * A "pass by" stop, geolocated on the real map. Dragging its pin updates
 * [position] in place, mirroring the web client's draggable via-stop pins.
 * Uses observable `by mutableStateOf` properties (rather than a data class)
 * so drags are reflected immediately without having to replace list
 * entries.
 */
class ViaStop(val id: Int, address: String = "", initialPosition: GeoPoint) {
    var address by mutableStateOf(address)
    var position by mutableStateOf(initialPosition)
}

data class RouteOption(
    val id: Int,
    val label: String,
    val distanceKm: Int,
    val durationMinutes: Int,
)

enum class PoiType { GAS, CAMPING, HOTEL, ROAD_WORK }

/** What a gas station offers — the amenities the POI server can filter gas stations by. */
enum class GasAmenity(val label: String) {
    GASOLINE("Gasoline"),
    ELECTRIC("Electric charging"),
    RESTAURANT("Restaurant"),
}

/**
 * A tappable marker on the map — a real POI (gas/camping/hotel, from the
 * PlanRoute POI server), an official Digitraffic road-work situation, or a
 * user-reported hazard. Only [isUserReport] markers can be withdrawn from
 * the callout; official/server data is informational, or addable as a stop.
 */
data class PoiMarker(
    val id: Int,
    val type: PoiType,
    val title: String,
    val description: String,
    val position: GeoPoint,
    val isUserReport: Boolean = false,
)

/** What the bottom sheet is currently showing. */
enum class SheetMode { PLANNING, COMPARING }
