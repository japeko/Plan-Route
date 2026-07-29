import type { LatLngTuple } from "leaflet";
import type { PoiAlongRouteRequestDto } from "@poi/shared";

export interface GeocodedPoint {
  label: string;
  position: LatLngTuple;
}

export interface NavigationStep {
  // Full sentence ("Turn left onto Main Street") — used for the spoken
  // voice instruction, where the verb phrase matters.
  instruction: string;
  // Arrow glyph for the maneuver (e.g. "←" for a left turn), so the
  // on-screen banner can show direction without spelling it out — there's
  // rarely enough width on a phone screen for both the full instruction
  // and the road name.
  arrow: string;
  // Road name (or a fallback like "Destination"), shown on-screen next to
  // the arrow instead of the full instruction sentence.
  roadLabel: string;
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
