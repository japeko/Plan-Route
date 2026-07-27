import type { LatLngTuple } from "leaflet";
import { OSRM_ROUTE_BASE_URL } from "@/constants/api.constants";

export class RoutingError extends Error {}

interface OsrmRoute {
  geometry: { coordinates: [number, number][] };
  distance: number;
  duration: number;
}

interface OsrmResponse {
  code: string;
  routes: OsrmRoute[];
}

export interface RoadRoute {
  path: LatLngTuple[];
  distanceMeters: number;
  durationSeconds: number;
}

export async function fetchRoadRoute(start: LatLngTuple, end: LatLngTuple): Promise<RoadRoute> {
  const coordinates = `${start[1]},${start[0]};${end[1]},${end[0]}`;
  const params = new URLSearchParams({ overview: "full", geometries: "geojson" });

  const response = await fetch(`${OSRM_ROUTE_BASE_URL}/${coordinates}?${params.toString()}`);

  if (!response.ok) {
    throw new RoutingError(`Routing request failed (status ${response.status}).`);
  }

  const data = (await response.json()) as OsrmResponse;
  const route = data.routes[0];

  if (data.code !== "Ok" || !route) {
    throw new RoutingError("No road route could be found between the two locations.");
  }

  return {
    path: route.geometry.coordinates.map(([lon, lat]) => [lat, lon]),
    distanceMeters: route.distance,
    durationSeconds: route.duration,
  };
}
