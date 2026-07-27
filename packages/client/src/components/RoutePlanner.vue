<script setup lang="ts">
import { ref } from "vue";
import { geocodeAddressInFinland } from "@/services/geocoding.service";
import { fetchRoadRoute } from "@/services/routing.service";
import type { RoutePlan } from "@/types/route.types";

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

function addStop(): void {
  viaAddresses.value.push("");
}

function removeStop(index: number): void {
  viaAddresses.value.splice(index, 1);
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
    const stops = await Promise.all(addresses.map((address) => geocodeAddressInFinland(address)));
    const road = await fetchRoadRoute(stops.map((stop) => stop.position));

    summary.value = {
      distanceKm: (road.distanceMeters / 1000).toFixed(1),
      durationMin: Math.round(road.durationSeconds / 60).toString(),
    };

    emit("route-planned", {
      stops,
      path: road.path,
      distanceMeters: road.distanceMeters,
      durationSeconds: road.durationSeconds,
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
      v-if="errorMessage"
      class="error"
    >
      {{ errorMessage }}
    </p>
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

.error {
  font-size: 0.85rem;
  color: #e03131;
}
</style>
