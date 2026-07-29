<script setup lang="ts">
import { ref } from "vue";
import MapView from "@/components/MapView.vue";
import RouteFilters from "@/components/RouteFilters.vue";
import RoutePlanner from "@/components/RoutePlanner.vue";
import { DEFAULT_POI_FILTERS } from "@/constants/filters.constants";
import type { PoiFilterOptions, RoutePlan } from "@/types/route.types";

const route = ref<RoutePlan | null>(null);
const filters = ref<PoiFilterOptions>({ ...DEFAULT_POI_FILTERS });
const isNavigating = ref(false);

function handleRouteCleared(): void {
  route.value = null;
  filters.value = { ...DEFAULT_POI_FILTERS };
}
</script>

<template>
  <div class="app">
    <aside
      class="sidebar"
      :class="{ 'sidebar--nav-hidden': isNavigating }"
    >
      <h1>Roadside Stops (Finland)</h1>
      <RoutePlanner
        @route-planned="route = $event"
        @route-cleared="handleRouteCleared"
      />
      <RouteFilters
        v-if="route"
        :filters="filters"
        @update:filters="filters = $event"
      />
    </aside>
    <main class="map-area">
      <MapView
        :route="route"
        :filters="filters"
        @navigating="isNavigating = $event"
      />
    </main>
  </div>
</template>

<style scoped>
.app {
  display: flex;
  width: 100vw;
  height: 100vh;
}

.sidebar {
  width: 320px;
  flex-shrink: 0;
  padding: 1rem;
  overflow-y: auto;
  background: #f8f9fa;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.sidebar h1 {
  font-size: 1.1rem;
  margin: 0;
}

.map-area {
  flex: 1;
}

@media (max-width: 640px) {
  .app {
    flex-direction: column;
  }

  .sidebar {
    width: 100%;
    max-height: 45vh;
  }

  .sidebar--nav-hidden {
    display: none;
  }

  .map-area {
    flex: 1;
    min-height: 0;
  }
}
</style>
