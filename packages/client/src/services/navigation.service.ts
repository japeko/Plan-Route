import L from "leaflet";
import type { LatLngTuple } from "leaflet";
import {
  NAVIGATION_VIEW_DISTANCE_MAX_METERS,
  NAVIGATION_VIEW_DISTANCE_MIN_METERS,
  NAVIGATION_VIEW_REFERENCE_MAX_WIDTH_PX,
  NAVIGATION_VIEW_REFERENCE_MIN_WIDTH_PX,
} from "@/constants/map.constants";
import type { NavigationStep } from "@/types/route.types";

export class GeolocationError extends Error {
  // Mirrors GeolocationPositionError.PERMISSION_DENIED (1) /
  // POSITION_UNAVAILABLE (2) / TIMEOUT (3) — undefined when geolocation
  // isn't supported at all. Only PERMISSION_DENIED is terminal; the
  // others are transient and watchPosition keeps calling back on its own.
  code?: number;

  constructor(message: string, code?: number) {
    super(message);
    this.code = code;
  }
}

export const GEOLOCATION_PERMISSION_DENIED = 1;

// How close the vehicle needs to be to a maneuver's location before we
// consider it passed and advance to the next step.
const STEP_ADVANCE_THRESHOLD_METERS = 40;

export function distanceMeters(a: LatLngTuple, b: LatLngTuple): number {
  return L.latLng(a).distanceTo(L.latLng(b));
}

// Scales the navigation look-ahead distance linearly with the map pane's
// pixel width (clamped to the reference range), so a laptop's much wider
// pane shows more upcoming road while a phone stays tightly zoomed in.
export function navigationViewDistanceMeters(mapPaneWidthPx: number): number {
  const clampedWidth = Math.min(
    Math.max(mapPaneWidthPx, NAVIGATION_VIEW_REFERENCE_MIN_WIDTH_PX),
    NAVIGATION_VIEW_REFERENCE_MAX_WIDTH_PX,
  );
  const t =
    (clampedWidth - NAVIGATION_VIEW_REFERENCE_MIN_WIDTH_PX) /
    (NAVIGATION_VIEW_REFERENCE_MAX_WIDTH_PX - NAVIGATION_VIEW_REFERENCE_MIN_WIDTH_PX);

  return Math.round(
    NAVIGATION_VIEW_DISTANCE_MIN_METERS + t * (NAVIGATION_VIEW_DISTANCE_MAX_METERS - NAVIGATION_VIEW_DISTANCE_MIN_METERS),
  );
}

// One-shot position fetch (unlike watchVehiclePosition's continuous
// tracking) — for "use my current location" as a route stop, not live
// navigation.
export function getCurrentPosition(): Promise<LatLngTuple> {
  return new Promise((resolve, reject) => {
    if (!navigator.geolocation) {
      reject(new GeolocationError("Geolocation is not supported by this browser."));
      return;
    }

    navigator.geolocation.getCurrentPosition(
      (position) => resolve([position.coords.latitude, position.coords.longitude]),
      (error) => reject(new GeolocationError(error.message, error.code)),
      { enableHighAccuracy: true, maximumAge: 5000, timeout: 15000 },
    );
  });
}

export function watchVehiclePosition(
  onUpdate: (position: LatLngTuple) => void,
  onError: (error: GeolocationError) => void,
): () => void {
  if (!navigator.geolocation) {
    onError(new GeolocationError("Geolocation is not supported by this browser."));
    return () => {};
  }

  const watchId = navigator.geolocation.watchPosition(
    (position) => onUpdate([position.coords.latitude, position.coords.longitude]),
    (error) => onError(new GeolocationError(error.message, error.code)),
    { enableHighAccuracy: true, maximumAge: 5000, timeout: 15000 },
  );

  return () => navigator.geolocation.clearWatch(watchId);
}

// Given where the vehicle is now and which step we were previously
// tracking toward, returns the step index to track next. Scans forward
// (never backward) from fromIndex for whichever step location the
// vehicle is currently closest to; if that's within range, it's been
// reached and we advance past it. Scanning the whole remainder rather
// than just checking the immediate next step means a sparse GPS update —
// a tunnel, a fast vehicle, a simulated jump — that lands the vehicle
// past several short steps at once still resolves correctly instead of
// getting stuck on a step that's already behind it.
export function resolveCurrentStepIndex(
  steps: NavigationStep[],
  vehiclePosition: LatLngTuple,
  fromIndex: number,
): number {
  const startIndex = Math.min(fromIndex, steps.length - 1);

  let closestIndex = startIndex;
  let closestDistance = Infinity;

  for (let i = startIndex; i < steps.length; i += 1) {
    const step = steps[i];
    if (!step) {
      continue;
    }
    const distance = distanceMeters(vehiclePosition, step.location);
    if (distance < closestDistance) {
      closestDistance = distance;
      closestIndex = i;
    }
  }

  if (closestDistance <= STEP_ADVANCE_THRESHOLD_METERS) {
    return Math.min(closestIndex + 1, steps.length - 1);
  }
  return startIndex;
}

// Index of whichever point on the route polyline the vehicle is
// currently nearest to — the anchor for "what's ahead" while navigating.
export function findNearestPathIndex(path: LatLngTuple[], position: LatLngTuple): number {
  let nearestIndex = 0;
  let nearestDistance = Infinity;

  path.forEach((point, index) => {
    const distance = distanceMeters(position, point);
    if (distance < nearestDistance) {
      nearestDistance = distance;
      nearestIndex = index;
    }
  });

  return nearestIndex;
}

// Walks forward along the route polyline from fromIndex, collecting
// points until roughly maxDistanceMeters of road has accumulated (or the
// route ends) — the actual upcoming road geometry to fit the map to,
// rather than a generic radius that ignores how the road curves.
export function sliceUpcomingPath(path: LatLngTuple[], fromIndex: number, maxDistanceMeters: number): LatLngTuple[] {
  const start = path[fromIndex];
  if (!start) {
    return [];
  }

  const slice: LatLngTuple[] = [start];
  let accumulated = 0;

  for (let i = fromIndex + 1; i < path.length && accumulated < maxDistanceMeters; i += 1) {
    const previous = path[i - 1];
    const point = path[i];
    if (!previous || !point) {
      break;
    }
    accumulated += distanceMeters(previous, point);
    slice.push(point);
  }

  return slice;
}
