<script setup lang="ts">
import { ref } from "vue";
import type { PointOfInterest } from "@poi/shared";
import MapView from "@/components/MapView.vue";
import RouteFilters from "@/components/RouteFilters.vue";
import RoutePlanner from "@/components/RoutePlanner.vue";
import VoiceSettings from "@/components/VoiceSettings.vue";
import { DEFAULT_POI_FILTERS } from "@/constants/filters.constants";
import type { PoiFilterOptions, RoutePlan } from "@/types/route.types";

const route = ref<RoutePlan | null>(null);
const filters = ref<PoiFilterOptions>({ ...DEFAULT_POI_FILTERS });
const isNavigating = ref(false);
const routePlannerRef = ref<InstanceType<typeof RoutePlanner> | null>(null);
const mapViewRef = ref<InstanceType<typeof MapView> | null>(null);
const sidebarEl = ref<HTMLElement | null>(null);
// Overrides the mobile-layout sidebar's default max-height (45vh, see the
// media query below) once the user drags the handle between it and the
// map — null means "use the CSS default", so desktop (where this handle
// is hidden) is never affected.
const mobileSidebarHeightPx = ref<number | null>(null);
let dragStartClientY = 0;
let dragStartHeightPx = 0;

function handleRouteCleared(): void {
  route.value = null;
  filters.value = { ...DEFAULT_POI_FILTERS };
}

function handleAddStopFromPoi(poi: PointOfInterest): void {
  routePlannerRef.value?.addPoiAsStop(poi);
}

function handleResizeMove(event: PointerEvent): void {
  const delta = event.clientY - dragStartClientY;
  // Keep both panes usable — never let either shrink to nothing.
  const maxHeightPx = window.innerHeight - 120;
  mobileSidebarHeightPx.value = Math.min(Math.max(dragStartHeightPx + delta, 120), maxHeightPx);
  // .map-area's size changes continuously as the sidebar is dragged, but
  // that's a pure CSS reflow — no window resize event fires for it — so
  // Leaflet needs to be told explicitly or it keeps rendering at a stale
  // cached size, leaving grey space (same issue as the nav-toggle
  // sidebar-hide case in MapView.vue, just triggered from here instead).
  mapViewRef.value?.invalidateSize();
}

function stopResize(): void {
  window.removeEventListener("pointermove", handleResizeMove);
  mapViewRef.value?.invalidateSize();
}

function startResize(event: PointerEvent): void {
  event.preventDefault();
  dragStartClientY = event.clientY;
  dragStartHeightPx = sidebarEl.value?.getBoundingClientRect().height ?? 0;
  window.addEventListener("pointermove", handleResizeMove);
  window.addEventListener("pointerup", stopResize, { once: true });
}
</script>

<template>
  <div class="app">
    <aside
      ref="sidebarEl"
      class="sidebar"
      :class="{ 'sidebar--nav-hidden': isNavigating }"
      :style="mobileSidebarHeightPx !== null ? { maxHeight: `${mobileSidebarHeightPx}px` } : undefined"
    >
      <h1>Roadside Stops (Finland)</h1>
      <VoiceSettings />
      <RoutePlanner
        ref="routePlannerRef"
        :is-navigating="isNavigating"
        @route-planned="route = $event"
        @route-cleared="handleRouteCleared"
      />
      <RouteFilters
        v-if="route"
        :filters="filters"
        @update:filters="filters = $event"
      />
    </aside>
    <div
      class="resize-handle"
      @pointerdown="startResize"
    >
      <span class="resize-handle-grip" />
    </div>
    <main class="map-area">
      <MapView
        ref="mapViewRef"
        :route="route"
        :filters="filters"
        @navigating="isNavigating = $event"
        @add-stop="handleAddStopFromPoi"
      />
    </main>
  </div>
</template>

<style scoped>
.app {
  display: flex;
  width: 100vw;
  height: 100vh;
  /* 100vh on mobile Safari/Chrome includes the area behind the address
     bar, pushing bottom-anchored absolute elements off-screen; 100dvh
     tracks the actual visible viewport. Kept 100vh above as a fallback
     for browsers that don't support dvh. */
  height: 100dvh;
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

.resize-handle {
  display: none;
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

  .sidebar--nav-hidden + .resize-handle {
    display: none;
  }

  .resize-handle {
    display: flex;
    flex-shrink: 0;
    align-items: center;
    justify-content: center;
    height: 2rem;
    background: #e9ecef;
    cursor: ns-resize;
    /* Without this, touch-dragging the handle also scrolls the page
       instead of just resizing. */
    touch-action: none;
  }

  .resize-handle-grip {
    width: 56px;
    height: 5px;
    border-radius: 2px;
    background: #adb5bd;
  }

  .map-area {
    flex: 1;
    min-height: 0;
  }
}
</style>
