import type { LatLngTuple } from "leaflet";
import type { PoiAlongRouteRequestDto } from "@poi/shared";

export interface GeocodedPoint {
  label: string;
  position: LatLngTuple;
}

// The only languages instructions are translated into — matches the
// languages selectable in the voice picker (English, plus Finland's two
// official languages).
export type NavigationLanguage = "en" | "fi" | "sv";

export interface NavigationStep {
  // Full sentence ("Turn left onto Main Street"), one per supported
  // navigation language — used for the spoken voice instruction, which
  // needs to match whatever language the selected voice speaks.
  instructions: Record<NavigationLanguage, string>;
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
