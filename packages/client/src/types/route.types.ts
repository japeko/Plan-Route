import type { LatLngTuple } from "leaflet";
import type { PoiAlongRouteRequestDto } from "@poi/shared";

export interface GeocodedPoint {
  label: string;
  position: LatLngTuple;
}

export interface NavigationStep {
  instruction: string;
  distanceMeters: number;
  durationSeconds: number;
  // Where this maneuver happens, so live navigation can tell how far the
  // vehicle is from it.
  location: LatLngTuple;
}

export interface RoutePlan {
  // Ordered start -> via stops -> end, in the sequence the route visits them.
  stops: GeocodedPoint[];
  path: LatLngTuple[];
  distanceMeters: number;
  durationSeconds: number;
  steps: NavigationStep[];
}

export type PoiFilterOptions = Omit<PoiAlongRouteRequestDto, "route">;
