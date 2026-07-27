import type { LatLngTuple } from "leaflet";
import type { PoiAlongRouteRequestDto } from "@poi/shared";

export interface GeocodedPoint {
  label: string;
  position: LatLngTuple;
}

export interface RoutePlan {
  // Ordered start -> via stops -> end, in the sequence the route visits them.
  stops: GeocodedPoint[];
  path: LatLngTuple[];
  distanceMeters: number;
  durationSeconds: number;
}

export type PoiFilterOptions = Omit<PoiAlongRouteRequestDto, "route">;
