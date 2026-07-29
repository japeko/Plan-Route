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
// cost of seeing fewer upcoming turns at a glance.
export const NAVIGATION_VIEW_DISTANCE_METERS = 500;
