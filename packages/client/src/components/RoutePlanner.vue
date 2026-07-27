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
const isPlanning = ref(false);
const errorMessage = ref<string | null>(null);
const summary = ref<{ distanceKm: string; durationMin: string } | null>(null);

async function planRoute(): Promise<void> {
  errorMessage.value = null;

  if (!startAddress.value.trim() || !endAddress.value.trim()) {
    errorMessage.value = "Enter both a start and an end address.";
    return;
  }

  isPlanning.value = true;
  try {
    const [start, end] = await Promise.all([
      geocodeAddressInFinland(startAddress.value),
      geocodeAddressInFinland(endAddress.value),
    ]);

    const road = await fetchRoadRoute(start.position, end.position);

    summary.value = {
      distanceKm: (road.distanceMeters / 1000).toFixed(1),
      durationMin: Math.round(road.durationSeconds / 60).toString(),
    };

    emit("route-planned", {
      start,
      end,
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

.actions {
  display: flex;
  gap: 0.5rem;
}

button {
  padding: 0.5rem 1rem;
  border: none;
  border-radius: 4px;
  background: #1c7ed6;
  color: white;
  font-size: 0.9rem;
  cursor: pointer;
}

button[type="button"] {
  background: #e9ecef;
  color: #495057;
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
