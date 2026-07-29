<script setup lang="ts">
import { computed } from "vue";
import type { FuelType } from "@poi/shared";
import type { PoiFilterOptions } from "@/types/route.types";

const props = defineProps<{ filters: PoiFilterOptions }>();
const emit = defineEmits<{ "update:filters": [filters: PoiFilterOptions] }>();

function update(patch: Partial<PoiFilterOptions>): void {
  emit("update:filters", { ...props.filters, ...patch });
}

function checked(event: Event): boolean {
  return (event.target as HTMLInputElement).checked;
}

const radiusKm = computed(() => props.filters.radiusMeters / 1000);

function setRadiusKm(event: Event): void {
  const km = Number((event.target as HTMLInputElement).value);
  update({ radiusMeters: Math.round(km * 1000) });
}

const campingRadiusKm = computed(() => props.filters.campingRadiusMeters / 1000);

function setCampingRadiusKm(event: Event): void {
  const km = Number((event.target as HTMLInputElement).value);
  update({ campingRadiusMeters: Math.round(km * 1000) });
}

function hasFuelType(fuel: FuelType): boolean {
  return props.filters.fuelTypes.includes(fuel);
}

function toggleFuelType(fuel: FuelType, event: Event): void {
  const next = checked(event)
    ? [...props.filters.fuelTypes, fuel]
    : props.filters.fuelTypes.filter((f) => f !== fuel);
  update({ fuelTypes: next });
}
</script>

<template>
  <div class="route-filters">
    <div class="field">
      <label for="radius">Show within {{ radiusKm.toFixed(1) }} km of route</label>
      <input
        id="radius"
        type="range"
        min="0.1"
        max="5"
        step="0.1"
        :value="radiusKm"
        @input="setRadiusKm"
      >
    </div>

    <label class="checkbox">
      <input
        type="checkbox"
        :checked="filters.showRestaurants"
        @change="update({ showRestaurants: checked($event) })"
      >
      Restaurants
    </label>

    <label class="checkbox">
      <input
        type="checkbox"
        :checked="filters.showGasStations"
        @change="update({ showGasStations: checked($event) })"
      >
      Gas stations
    </label>

    <div
      v-if="filters.showGasStations"
      class="sub-options"
    >
      <label class="checkbox">
        <input
          type="checkbox"
          :checked="hasFuelType('gasoline')"
          @change="toggleFuelType('gasoline', $event)"
        >
        Gasoline
      </label>
      <label class="checkbox">
        <input
          type="checkbox"
          :checked="hasFuelType('electric')"
          @change="toggleFuelType('electric', $event)"
        >
        Electric charging
      </label>
      <label class="checkbox">
        <input
          type="checkbox"
          :checked="filters.onlyWithRestaurant"
          @change="update({ onlyWithRestaurant: checked($event) })"
        >
        Only stations with a restaurant
      </label>
    </div>

    <label class="checkbox">
      <input
        type="checkbox"
        :checked="filters.showCamping"
        @change="update({ showCamping: checked($event) })"
      >
      Camping areas
    </label>

    <div
      v-if="filters.showCamping"
      class="sub-options"
    >
      <div class="field">
        <label for="camping-radius">Show within {{ campingRadiusKm.toFixed(1) }} km of route</label>
        <input
          id="camping-radius"
          type="range"
          min="1"
          max="50"
          step="1"
          :value="campingRadiusKm"
          @input="setCampingRadiusKm"
        >
      </div>
    </div>
  </div>
</template>

<style scoped>
.route-filters {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  padding: 1rem;
  background: white;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.15);
  font-size: 0.85rem;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.field label {
  font-size: 0.8rem;
  font-weight: 600;
  color: #495057;
}

.checkbox {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  cursor: pointer;
}

.sub-options {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  padding-left: 1.25rem;
  border-left: 2px solid #e9ecef;
}
</style>
