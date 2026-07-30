<script setup lang="ts">
import { onMounted, ref } from "vue";
import type { LatLngTuple } from "leaflet";
import type { PointOfInterest } from "@poi/shared";
import { geocodeAddressInFinland, reverseGeocode } from "@/services/geocoding.service";
import { GeolocationError, getCurrentPosition } from "@/services/navigation.service";
import { fetchRoadTrip, fetchRouteAlternatives } from "@/services/routing.service";
import type { RouteAlternative } from "@/services/routing.service";
import { currentLanguage } from "@/services/speech.service";
import type { GeocodedPoint, NavigationStep, RoutePlan } from "@/types/route.types";
import { formatDistance, formatDuration } from "@/utils/format";

defineProps<{ isNavigating: boolean }>();
const emit = defineEmits<{
  "route-planned": [route: RoutePlan];
  "route-cleared": [];
}>();

// Text shown in the field, plus an optional exact-position override so a
// stop added from a POI marker (or "use my current location") routes to
// its real coordinates rather than being re-geocoded back from a label —
// which could land somewhere slightly different, especially for a POI
// name that isn't itself a searchable address.
interface AddressEntry {
  text: string;
  override: GeocodedPoint | null;
}

function createEntry(text = ""): AddressEntry {
  return { text, override: null };
}

const startAddress = ref("");
const endAddress = ref("");
const viaStops = ref<AddressEntry[]>([]);
const isPlanning = ref(false);
const errorMessage = ref<string | null>(null);
// Set when "Use my current location" resolves, so planRoute() can use the
// exact GPS fix directly instead of re-geocoding the address text (which
// would just approximate it back from the reverse-geocoded label).
const startOverride = ref<GeocodedPoint | null>(null);
const isLocating = ref(false);
const summary = ref<{ distanceKm: string; duration: string } | null>(null);
const wasReordered = ref(false);
const directions = ref<NavigationStep[]>([]);
const showDirections = ref(false);
// Populated only for a direct start->end trip (no via stops) when OSRM's
// Route service returns more than one genuinely distinct option — the
// user picks one via selectRouteChoice() before it's actually applied.
const routeChoices = ref<RouteAlternative[] | null>(null);
const pendingChoiceStops = ref<GeocodedPoint[] | null>(null);
// Which routeChoices entry is currently applied — kept (rather than
// clearing routeChoices on pick) so both options stay visible to compare
// and switch between without re-planning.
const selectedChoiceIndex = ref<number | null>(null);
// The exact resolved stops behind whichever route is currently active —
// used for "Share route" so the link encodes real coordinates rather
// than address text that would need re-geocoding (and could resolve
// slightly differently) on the receiving device.
const lastResolvedStops = ref<GeocodedPoint[] | null>(null);
const shareUrl = ref<string | null>(null);
const shareCopied = ref(false);

function addStop(): void {
  viaStops.value.push(createEntry());
}

function removeStop(index: number): void {
  viaStops.value.splice(index, 1);
}

function clearViaOverride(index: number): void {
  const entry = viaStops.value[index];
  if (entry) {
    entry.override = null;
  }
}

function clearStartOverride(): void {
  startOverride.value = null;
}

// Called from App.vue when the user taps "Add as stop" on a POI popup.
function addPoiAsStop(poi: PointOfInterest): void {
  const [lng, lat] = poi.location.coordinates;
  viaStops.value.push({ text: poi.name, override: { label: poi.name, position: [lat, lng] } });
  void planRoute();
}

defineExpose({ addPoiAsStop });

async function useCurrentLocationAsStart(): Promise<void> {
  errorMessage.value = null;
  isLocating.value = true;
  try {
    const position = await getCurrentPosition();
    // A reverse-geocode failure shouldn't block using the GPS fix itself
    // — fall back to a generic label rather than losing the position.
    const label = await reverseGeocode(position)
      .then((result) => result.label)
      .catch(() => "Current location");

    startOverride.value = { label, position };
    startAddress.value = label;
  } catch (err) {
    errorMessage.value =
      err instanceof GeolocationError ? err.message : "Failed to get your current location.";
  } finally {
    isLocating.value = false;
  }
}

// Replaces the generic "Arrive at your destination" (which OSRM gives
// every arrival step, intermediate or final) with the actual stop name.
function withArrivalLabel(step: NavigationStep, arrivalLabel: string): NavigationStep {
  return {
    ...step,
    instructions: {
      en: `Arrive at ${arrivalLabel}`,
      fi: `Saavuit kohteeseen ${arrivalLabel}`,
      sv: `Du har anlänt till ${arrivalLabel}`,
    },
    roadLabel: arrivalLabel,
  };
}

function finalizeRoute(
  stops: GeocodedPoint[],
  path: LatLngTuple[],
  distanceMeters: number,
  durationSeconds: number,
  steps: NavigationStep[],
  reordered: boolean,
): void {
  directions.value = steps;
  wasReordered.value = reordered;
  lastResolvedStops.value = stops;
  shareUrl.value = null;
  shareCopied.value = false;
  summary.value = {
    distanceKm: (distanceMeters / 1000).toFixed(1),
    duration: formatDuration(durationSeconds),
  };
  emit("route-planned", { stops, path, distanceMeters, durationSeconds, steps });
}

function applyRouteAlternative(stops: GeocodedPoint[], alternative: RouteAlternative): void {
  const arrivalLabel = stops[1]?.label ?? "your destination";
  const steps = alternative.steps.map((step, index, all) =>
    index === all.length - 1 ? withArrivalLabel(step, arrivalLabel) : step,
  );
  finalizeRoute(stops, alternative.path, alternative.distanceMeters, alternative.durationSeconds, steps, false);
}

// Called when the user picks a card from the route-choice list. Both
// cards stay visible afterward (routeChoices isn't cleared) so the user
// can compare and switch between them freely without re-planning.
function selectRouteChoice(index: number): void {
  const alternative = routeChoices.value?.[index];
  const stops = pendingChoiceStops.value;
  if (!alternative || !stops) {
    return;
  }
  applyRouteAlternative(stops, alternative);
  selectedChoiceIndex.value = index;
}

async function planRoute(): Promise<void> {
  errorMessage.value = null;
  routeChoices.value = null;
  pendingChoiceStops.value = null;
  selectedChoiceIndex.value = null;

  if (!startAddress.value.trim() || !endAddress.value.trim()) {
    errorMessage.value = "Enter both a start and an end address.";
    return;
  }

  // Snapshotted once rather than read while resolving, so a later
  // reassignment (e.g. clearRoute()) can't retroactively change which
  // stops this planRoute() call resolves.
  const viaEntries = viaStops.value.filter((entry) => entry.override || entry.text.trim().length > 0);
  const entries: AddressEntry[] = [
    { text: startAddress.value, override: startOverride.value },
    ...viaEntries,
    { text: endAddress.value, override: null },
  ];

  isPlanning.value = true;
  try {
    const geocoded = await Promise.all(
      entries.map((entry) => entry.override ?? geocodeAddressInFinland(entry.text)),
    );

    // OSRM's alternatives only work for a plain two-point trip — Trip's
    // waypoint-reordering solver doesn't support them, so any via stops
    // mean falling back to the single-route Trip flow below.
    if (viaEntries.length === 0) {
      const [startPoint, endPoint] = geocoded;
      if (startPoint && endPoint) {
        const alternatives = await fetchRouteAlternatives(startPoint.position, endPoint.position);
        const onlyAlternative = alternatives[0];
        if (alternatives.length <= 1) {
          if (onlyAlternative) {
            applyRouteAlternative([startPoint, endPoint], onlyAlternative);
          }
        } else {
          routeChoices.value = alternatives;
          pendingChoiceStops.value = [startPoint, endPoint];
          // Apply the first option immediately so a route is already
          // active (and highlighted) rather than showing an empty map
          // until the user picks one.
          selectRouteChoice(0);
        }
      }
      return;
    }

    const trip = await fetchRoadTrip(geocoded.map((stop) => stop.position));

    const orderedStops = trip.visitOrder
      .map((originalIndex) => geocoded[originalIndex])
      .filter((stop): stop is GeocodedPoint => stop !== undefined);
    const reordered = trip.visitOrder.some((originalIndex, position) => originalIndex !== position);

    // Each leg's OSRM "arrive" step is generic ("Arrive at your
    // destination") even for an intermediate stop — replace it with the
    // actual stop name so multi-stop directions read correctly.
    const steps: NavigationStep[] = trip.legs.flatMap((legSteps, legIndex) =>
      legSteps.map((step, stepIndex) => {
        if (stepIndex !== legSteps.length - 1) {
          return step;
        }
        const arrivalStop = orderedStops[legIndex + 1];
        return arrivalStop ? withArrivalLabel(step, arrivalStop.label) : step;
      }),
    );

    finalizeRoute(orderedStops, trip.path, trip.distanceMeters, trip.durationSeconds, steps, reordered);
  } catch (err) {
    errorMessage.value = err instanceof Error ? err.message : "Failed to plan the route.";
  } finally {
    isPlanning.value = false;
  }
}

function clearRoute(): void {
  startAddress.value = "";
  startOverride.value = null;
  endAddress.value = "";
  viaStops.value = [];
  summary.value = null;
  errorMessage.value = null;
  wasReordered.value = false;
  directions.value = [];
  showDirections.value = false;
  routeChoices.value = null;
  pendingChoiceStops.value = null;
  selectedChoiceIndex.value = null;
  lastResolvedStops.value = null;
  shareUrl.value = null;
  shareCopied.value = false;
  emit("route-cleared");
}

// One query param holding the whole route as JSON — URLSearchParams
// handles the encoding/decoding symmetrically, so nothing extra needs
// escaping here even though address labels are free text.
const SHARE_QUERY_PARAM = "route";

interface SharedRoutePayload {
  start: GeocodedPoint;
  via: GeocodedPoint[];
  end: GeocodedPoint;
}

async function shareRoute(): Promise<void> {
  const stops = lastResolvedStops.value;
  if (!stops || stops.length < 2) {
    return;
  }
  const [start, ...rest] = stops;
  const end = rest.pop();
  if (!start || !end) {
    return;
  }
  const payload: SharedRoutePayload = { start, via: rest, end };

  const url = new URL(window.location.href);
  url.search = "";
  url.searchParams.set(SHARE_QUERY_PARAM, JSON.stringify(payload));
  shareUrl.value = url.toString();
  shareCopied.value = false;

  try {
    await navigator.clipboard.writeText(shareUrl.value);
    shareCopied.value = true;
  } catch {
    // Clipboard API can fail (permissions, insecure context, older
    // browsers) — the link is still shown in the readonly field below
    // for the user to select and copy manually.
  }
}

// Loads a route shared from another device (see shareRoute above) —
// restores the exact stops as overrides (skipping re-geocoding, which
// could resolve slightly differently) and plans it immediately.
onMounted(() => {
  const encoded = new URLSearchParams(window.location.search).get(SHARE_QUERY_PARAM);
  if (!encoded) {
    return;
  }

  // Strip the (potentially large) param from the visible URL regardless
  // of whether parsing succeeds, so it doesn't linger in the address bar
  // or get carried along by a later browser-level share/bookmark.
  window.history.replaceState(null, "", window.location.pathname);

  try {
    const payload = JSON.parse(encoded) as SharedRoutePayload;
    startAddress.value = payload.start.label;
    startOverride.value = payload.start;
    viaStops.value = payload.via.map((stop) => ({ text: stop.label, override: stop }));
    endAddress.value = payload.end.label;
    void planRoute();
  } catch {
    errorMessage.value = "That shared route link looks invalid.";
  }
});
</script>

<template>
  <form
    class="route-planner"
    @submit.prevent="planRoute"
  >
    <div class="field">
      <label for="start-address">Start address</label>
      <div class="start-row">
        <input
          id="start-address"
          v-model="startAddress"
          type="text"
          placeholder="e.g. Mannerheimintie 1, Helsinki"
          @input="clearStartOverride"
        >
        <button
          type="button"
          class="use-location"
          title="Use my current location"
          :disabled="isLocating"
          @click="useCurrentLocationAsStart"
        >
          {{ isLocating ? "Locating…" : "📍" }}
        </button>
      </div>
    </div>

    <div
      v-for="(stop, index) in viaStops"
      :key="index"
      class="field via-field"
    >
      <label :for="`via-address-${index}`">Pass by</label>
      <div class="via-row">
        <input
          :id="`via-address-${index}`"
          v-model="stop.text"
          type="text"
          placeholder="e.g. Hämeenlinna"
          @input="clearViaOverride(index)"
        >
        <button
          type="button"
          class="remove-stop"
          title="Remove stop"
          @click="removeStop(index)"
        >
          &times;
        </button>
      </div>
    </div>

    <button
      type="button"
      class="add-stop"
      @click="addStop"
    >
      + Add stop
    </button>

    <div class="field">
      <label for="end-address">End address</label>
      <input
        id="end-address"
        v-model="endAddress"
        type="text"
        placeholder="e.g. Hämeenkatu 1, Tampere"
      >
    </div>

    <div class="actions">
      <button
        type="submit"
        :disabled="isPlanning"
      >
        {{ isPlanning ? "Planning…" : "Plan route" }}
      </button>
      <button
        type="button"
        :disabled="isPlanning"
        @click="clearRoute"
      >
        Clear
      </button>
      <button
        v-if="lastResolvedStops"
        type="button"
        class="share-route"
        @click="shareRoute"
      >
        Share route
      </button>
    </div>
    <div
      v-if="shareUrl"
      class="field"
    >
      <label for="share-url">Open this link on another device to load the route</label>
      <input
        id="share-url"
        type="text"
        class="share-url-input"
        :value="shareUrl"
        readonly
        @focus="($event.target as HTMLInputElement).select()"
      >
      <p
        v-if="shareCopied"
        class="share-copied-note"
      >
        Link copied to clipboard!
      </p>
    </div>
    <div
      v-if="routeChoices && !isNavigating"
      class="route-choices"
    >
      <p class="route-choices-label">
        Choose a route:
      </p>
      <button
        v-for="(choice, index) in routeChoices"
        :key="index"
        type="button"
        class="route-choice"
        :class="{ 'route-choice--selected': index === selectedChoiceIndex }"
        @click="selectRouteChoice(index)"
      >
        Route {{ index + 1 }}: {{ (choice.distanceMeters / 1000).toFixed(1) }} km &middot;
        {{ formatDuration(choice.durationSeconds) }}
      </button>
    </div>
    <p
      v-if="summary"
      class="summary"
    >
      {{ summary.distanceKm }} km &middot; {{ summary.duration }}
    </p>
    <p
      v-if="wasReordered"
      class="reorder-note"
    >
      Stops reordered for the shortest route.
    </p>
    <p
      v-if="errorMessage"
      class="error"
    >
      {{ errorMessage }}
    </p>

    <button
      v-if="directions.length > 0"
      type="button"
      class="toggle-directions"
      @click="showDirections = !showDirections"
    >
      {{ showDirections ? "Hide directions" : `Show directions (${directions.length})` }}
    </button>
    <ol
      v-if="showDirections"
      class="directions"
    >
      <li
        v-for="(step, index) in directions"
        :key="index"
      >
        <span class="instruction">{{ step.instructions[currentLanguage] }}</span>
        <span class="step-distance">{{ formatDistance(step.distanceMeters) }}</span>
      </li>
    </ol>
  </form>
</template>

<style scoped>
.route-planner {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding: 1rem;
  background: white;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.15);
}

.field {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

label {
  font-size: 0.8rem;
  font-weight: 600;
  color: #495057;
}

input {
  padding: 0.5rem;
  border: 1px solid #ced4da;
  border-radius: 4px;
  font-size: 0.9rem;
}

.via-row {
  display: flex;
  gap: 0.4rem;
}

.via-row input {
  flex: 1;
  min-width: 0;
}

.remove-stop {
  flex-shrink: 0;
  width: 2rem;
  border: 1px solid #ced4da;
  border-radius: 4px;
  background: #f8f9fa;
  color: #495057;
  font-size: 1.1rem;
  line-height: 1;
  cursor: pointer;
}

.start-row {
  display: flex;
  gap: 0.4rem;
}

.start-row input {
  flex: 1;
  min-width: 0;
}

.use-location {
  flex-shrink: 0;
  padding: 0 0.6rem;
  border: 1px solid #ced4da;
  border-radius: 4px;
  background: #f8f9fa;
  color: #495057;
  font-size: 0.9rem;
  white-space: nowrap;
  cursor: pointer;
}

.add-stop {
  align-self: flex-start;
  padding: 0.3rem 0.6rem;
  border: 1px dashed #adb5bd;
  border-radius: 4px;
  background: none;
  color: #495057;
  font-size: 0.8rem;
  cursor: pointer;
}

.actions {
  display: flex;
  gap: 0.5rem;
}

button[type="submit"] {
  padding: 0.5rem 1rem;
  border: none;
  border-radius: 4px;
  background: #1c7ed6;
  color: white;
  font-size: 0.9rem;
  cursor: pointer;
}

.actions button[type="button"] {
  padding: 0.5rem 1rem;
  border: none;
  border-radius: 4px;
  background: #e9ecef;
  color: #495057;
  font-size: 0.9rem;
  cursor: pointer;
}

.actions .share-route {
  background: #e7f5ff;
  color: #1c7ed6;
}

.share-url-input {
  font-size: 0.8rem;
  color: #495057;
}

.share-copied-note {
  margin: 0;
  font-size: 0.8rem;
  color: #2f9e44;
  font-weight: 600;
}

button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.route-choices {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.route-choices-label {
  font-size: 0.8rem;
  font-weight: 600;
  color: #495057;
  margin: 0;
}

.route-choice {
  padding: 0.5rem 0.75rem;
  border: 1px solid #1c7ed6;
  border-radius: 4px;
  background: #e7f5ff;
  color: #1c7ed6;
  font-size: 0.85rem;
  font-weight: 600;
  text-align: left;
  cursor: pointer;
}

.route-choice--selected {
  background: #1c7ed6;
  color: white;
}

.summary {
  font-size: 0.85rem;
  color: #2f9e44;
  font-weight: 600;
}

.reorder-note {
  font-size: 0.8rem;
  color: #495057;
  font-style: italic;
}

.error {
  font-size: 0.85rem;
  color: #e03131;
}

.toggle-directions {
  align-self: flex-start;
  padding: 0.3rem 0.6rem;
  border: 1px solid #ced4da;
  border-radius: 4px;
  background: #f8f9fa;
  color: #1c7ed6;
  font-size: 0.8rem;
  font-weight: 600;
  cursor: pointer;
}

.directions {
  max-height: 260px;
  overflow-y: auto;
  margin: 0;
  padding-left: 1.25rem;
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  font-size: 0.8rem;
}

.directions li {
  display: flex;
  justify-content: space-between;
  gap: 0.5rem;
}

.directions .instruction {
  color: #212529;
}

.directions .step-distance {
  flex-shrink: 0;
  color: #868e96;
}
</style>
