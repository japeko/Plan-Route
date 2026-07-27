import type { LatLngTuple } from "leaflet";
import type { PoiAlongRouteRequestDto } from "@poi/shared";

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

export type PoiFilterOptions = Omit<PoiAlongRouteRequestDto, "route">;
