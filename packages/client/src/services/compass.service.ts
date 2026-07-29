import { ref } from "vue";

// Compass heading in degrees clockwise from true north (0 = north, 90 =
// east, ...), or null until a valid reading arrives.
export const heading = ref<number | null>(null);
export const compassError = ref<string | null>(null);

let listening = false;

// iOS Safari doesn't implement the standard "absolute" orientation event
// at all, but exposes its own non-standard, already-absolute compass
// heading directly on the event — more reliable there than alpha.
interface WebkitOrientationEvent extends DeviceOrientationEvent {
  webkitCompassHeading?: number;
}

function normalizeHeading(event: DeviceOrientationEvent): number | null {
  const webkitHeading = (event as WebkitOrientationEvent).webkitCompassHeading;
  if (typeof webkitHeading === "number") {
    return webkitHeading;
  }

  // Standard DeviceOrientationEvent: alpha increases counter-clockwise
  // from wherever the device happened to be pointed when tracking
  // started, and only lines up with true compass north when the browser
  // reports the reading as absolute (locked to Earth's frame rather than
  // the device's arbitrary starting orientation).
  if (event.alpha === null || !event.absolute) {
    return null;
  }
  return 360 - event.alpha;
}

function handleOrientation(event: DeviceOrientationEvent): void {
  const value = normalizeHeading(event);
  if (value !== null) {
    heading.value = value;
    compassError.value = null;
  }
}

export async function startCompass(): Promise<void> {
  if (listening) {
    return;
  }
  compassError.value = null;

  if (!("DeviceOrientationEvent" in window)) {
    compassError.value = "Compass isn't supported by this browser.";
    return;
  }

  // iOS 13+ gates device orientation behind an explicit permission
  // prompt, which only works when triggered directly from a user
  // gesture (e.g. tapping the compass widget) — other browsers don't
  // have this API at all and just start receiving events immediately.
  const requestPermission = (
    DeviceOrientationEvent as unknown as { requestPermission?: () => Promise<"granted" | "denied"> }
  ).requestPermission;

  if (typeof requestPermission === "function") {
    try {
      const result = await requestPermission();
      if (result !== "granted") {
        compassError.value = "Compass permission denied.";
        return;
      }
    } catch {
      compassError.value = "Failed to request compass permission.";
      return;
    }
  }

  window.addEventListener("deviceorientationabsolute", handleOrientation);
  window.addEventListener("deviceorientation", handleOrientation);
  listening = true;
}

export function stopCompass(): void {
  if (!listening) {
    return;
  }
  window.removeEventListener("deviceorientationabsolute", handleOrientation);
  window.removeEventListener("deviceorientation", handleOrientation);
  listening = false;
  heading.value = null;
}
