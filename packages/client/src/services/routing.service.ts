import type { LatLngTuple } from "leaflet";
import { OSRM_TRIP_BASE_URL } from "@/constants/api.constants";

export class RoutingError extends Error {}

interface OsrmTrip {
  geometry: { coordinates: [number, number][] };
  distance: number;
  duration: number;
}

interface OsrmWaypoint {
  waypoint_index: number;
}

interface OsrmTripResponse {
  code: string;
  trips: OsrmTrip[];
  waypoints: OsrmWaypoint[];
}

export interface RoadTrip {
  path: LatLngTuple[];
  distanceMeters: number;
  durationSeconds: number;
  // Original-array indices of `stops`, reordered into the order the trip
  // actually visits them (start and end stay fixed; only via stops move).
  visitOrder: number[];
}

export async function fetchRoadTrip(stops: LatLngTuple[]): Promise<RoadTrip> {
  if (stops.length < 2) {
    throw new RoutingError("At least a start and an end location are required.");
  }

  const coordinates = stops.map(([lat, lng]) => `${lng},${lat}`).join(";");
  const params = new URLSearchParams({
    overview: "full",
    geometries: "geojson",
    source: "first",
    destination: "last",
    roundtrip: "false",
  });

  const response = await fetch(`${OSRM_TRIP_BASE_URL}/${coordinates}?${params.toString()}`);

  if (!response.ok) {
    throw new RoutingError(`Routing request failed (status ${response.status}).`);
  }

  const data = (await response.json()) as OsrmTripResponse;
  const trip = data.trips[0];

  if (data.code !== "Ok" || !trip) {
    throw new RoutingError("No road route could be found through the given locations.");
  }

  const visitOrder = data.waypoints
    .map((waypoint, originalIndex) => ({ originalIndex, visitPosition: waypoint.waypoint_index }))
    .sort((a, b) => a.visitPosition - b.visitPosition)
    .map((entry) => entry.originalIndex);

  return {
    path: trip.geometry.coordinates.map(([lon, lat]) => [lat, lon]),
    distanceMeters: trip.distance,
    durationSeconds: trip.duration,
    visitOrder,
  };
}
