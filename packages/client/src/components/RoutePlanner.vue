<script setup lang="ts">
import { ref } from "vue";
import { geocodeAddressInFinland } from "@/services/geocoding.service";
import { fetchRoadTrip } from "@/services/routing.service";
import type { GeocodedPoint, NavigationStep, RoutePlan } from "@/types/route.types";

const emit = defineEmits<{
  "route-planned": [route: RoutePlan];
  "route-cleared": [];
}>();

const startAddress = ref("");
const endAddress = ref("");
const viaAddresses = ref<string[]>([]);
const isPlanning = ref(false);
const errorMessage = ref<string | null>(null);
const summary = ref<{ distanceKm: string; durationMin: string } | null>(null);
const wasReordered = ref(false);
const directions = ref<NavigationStep[]>([]);
const showDirections = ref(false);

function addStop(): void {
  viaAddresses.value.push("");
}

function removeStop(index: number): void {
  viaAddresses.value.splice(index, 1);
}

function formatDistance(distanceMeters: number): string {
  return distanceMeters >= 1000 ? `${(distanceMeters / 1000).toFixed(1)} km` : `${Math.round(distanceMeters)} m`;
}

async function planRoute(): Promise<void> {
  errorMessage.value = null;

  if (!startAddress.value.trim() || !endAddress.value.trim()) {
    errorMessage.value = "Enter both a start and an end address.";
    return;
  }

  const viaEntries = viaAddresses.value.map((address) => address.trim()).filter((address) => address.length > 0);
  const addresses = [startAddress.value, ...viaEntries, endAddress.value];

  isPlanning.value = true;
  try {
    const geocoded = await Promise.all(addresses.map((address) => geocodeAddressInFinland(address)));
    const trip = await fetchRoadTrip(geocoded.map((stop) => stop.position));

    const orderedStops = trip.visitOrder
      .map((originalIndex) => geocoded[originalIndex])
      .filter((stop): stop is GeocodedPoint => stop !== undefined);
    wasReordered.value = trip.visitOrder.some((originalIndex, position) => originalIndex !== position);

    // Each leg's OSRM "arrive" step is generic ("Arrive at your
    // destination") even for an intermediate stop — replace it with the
    // actual stop name so multi-stop directions read correctly.
    const steps: NavigationStep[] = trip.legs.flatMap((legSteps, legIndex) =>
      legSteps.map((step, stepIndex) => {
        if (stepIndex !== legSteps.length - 1) {
          return step;
        }
        const arrivalStop = orderedStops[legIndex + 1];
        return arrivalStop ? { ...step, instruction: `Arrive at ${arrivalStop.label}` } : step;
      }),
    );
    directions.value = steps;

    summary.value = {
      distanceKm: (trip.distanceMeters / 1000).toFixed(1),
      durationMin: Math.round(trip.durationSeconds / 60).toString(),
    };

    emit("route-planned", {
      stops: orderedStops,
      path: trip.path,
      distanceMeters: trip.distanceMeters,
      durationSeconds: trip.durationSeconds,
      steps,
    });
  } catch (err) {
    errorMessage.value = err instanceof Error ? err.message : "Failed to plan the route.";
  } finally {
    isPlanning.value = false;
  }
}

function clearRoute(): void {
  startAddress.value = "";
  endAddress.value = "";
  viaAddresses.value = [];
  summary.value = null;
  errorMessage.value = null;
  wasReordered.value = false;
  directions.value = [];
  showDirections.value = false;
  emit("route-cleared");
}
</script>

<template>
  <form
    class="route-planner"
    @submit.prevent="planRoute"
  >
    <div class="field">
      <label for="start-address">Start address</label>
      <input
        id="start-address"
        v-model="startAddress"
        type="text"
        placeholder="e.g. Mannerheimintie 1, Helsinki"
      >
    </div>

    <div
      v-for="(_, index) in viaAddresses"
      :key="index"
      class="field via-field"
    >
      <label :for="`via-address-${index}`">Pass by</label>
      <div class="via-row">
        <input
          :id="`via-address-${index}`"
          v-model="viaAddresses[index]"
          type="text"
          placeholder="e.g. Hämeenlinna"
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
    </div>
    <p
      v-if="summary"
      class="summary"
    >
      {{ summary.distanceKm }} km &middot; {{ summary.durationMin }} min
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
        <span class="instruction">{{ step.instruction }}</span>
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

button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
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
