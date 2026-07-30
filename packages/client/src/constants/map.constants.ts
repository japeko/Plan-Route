import type { LatLngBoundsExpression, LatLngTuple } from "leaflet";

// Roughly the extent of mainland + archipelago Finland.
export const FINLAND_BOUNDS: LatLngBoundsExpression = [
  [59.4, 19.0],
  [70.2, 31.7],
];

export const FINLAND_CENTER: LatLngTuple = [64.9, 26.0];

export const FINLAND_DEFAULT_ZOOM = 6;
export const FINLAND_MIN_ZOOM = 5;

export const OSM_TILE_LAYER_URL = "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png";
export const OSM_TILE_LAYER_ATTRIBUTION =
  '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors';

// While live-navigating, the map follows the vehicle and shows roughly
// this much of the upcoming road ahead, rather than the whole route.
// Smaller = more zoomed in (roads/street names easier to read), at the
// cost of seeing fewer upcoming turns at a glance. Scaled by the map
// pane's actual pixel width (see navigationViewDistanceMeters in
// navigation.service.ts) between these two values, rather than fixed —
// a laptop's much wider map pane can show more road ahead at the same
// effective zoom that a phone needs this tight to stay readable.
export const NAVIGATION_VIEW_DISTANCE_MIN_METERS = 500;
export const NAVIGATION_VIEW_DISTANCE_MAX_METERS = 2000;

// Map-pane pixel widths the min/max distances above are calibrated
// against — roughly a phone's full-width portrait viewport vs. a
// laptop's map pane (window width minus the ~320px sidebar).
export const NAVIGATION_VIEW_REFERENCE_MIN_WIDTH_PX = 400;
export const NAVIGATION_VIEW_REFERENCE_MAX_WIDTH_PX = 1400;

// The view also scales with vehicle speed — faster travel needs more
// upcoming road visible (longer stopping/reaction distance, turns approach
// faster), on top of the screen-size scaling above. A step function
// rather than continuous: the view stays fixed within a bracket and only
// changes when speed crosses into the next one, so it doesn't creep
// in/out constantly with every small speed fluctuation. maxKmh is the
// upper (inclusive) bound of each bracket; the last entry's Infinity
// catches everything above the second-to-last bound.
export const SPEED_VIEW_DISTANCE_BRACKETS: { maxKmh: number; multiplier: number }[] = [
  { maxKmh: 50, multiplier: 1 },
  { maxKmh: 70, multiplier: 1.3 },
  { maxKmh: 90, multiplier: 1.6 },
  { maxKmh: 110, multiplier: 2 },
  { maxKmh: Infinity, multiplier: 2.5 },
];
