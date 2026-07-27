import L from "leaflet";
import type { LatLngTuple } from "leaflet";
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
