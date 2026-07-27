import type { LatLngTuple } from "leaflet";

export interface GeocodedPoint {
  label: string;
  position: LatLngTuple;
}

export interface RoutePlan {
  start: GeocodedPoint;
  end: GeocodedPoint;
  path: LatLngTuple[];
  distanceMeters: number;
  durationSeconds: number;
}
