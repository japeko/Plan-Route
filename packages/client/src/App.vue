<script setup lang="ts">
import { ref } from "vue";
import MapView from "@/components/MapView.vue";
import RoutePlanner from "@/components/RoutePlanner.vue";
import type { RoutePlan } from "@/types/route.types";

const route = ref<RoutePlan | null>(null);
</script>

<template>
  <div class="app">
    <aside class="sidebar">
      <h1>Finland: Gas Stations &amp; Restaurants</h1>
      <RoutePlanner
        @route-planned="route = $event"
        @route-cleared="route = null"
      />
    </aside>
    <main class="map-area">
      <MapView :route="route" />
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
}

.sidebar h1 {
  font-size: 1.1rem;
  margin: 0 0 1rem;
}

.map-area {
  flex: 1;
}
</style>
