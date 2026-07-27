<script setup lang="ts">
import L from "leaflet";
import "leaflet/dist/leaflet.css";
import { onMounted, onUnmounted, ref, watch } from "vue";
import type { PointOfInterest } from "@poi/shared";
import { fetchPoisInViewport } from "@/api/poi.api";
import {
  FINLAND_BOUNDS,
  FINLAND_CENTER,
  FINLAND_DEFAULT_ZOOM,
  FINLAND_MIN_ZOOM,
  OSM_TILE_LAYER_ATTRIBUTION,
  OSM_TILE_LAYER_URL,
} from "@/constants/map.constants";
import type { RoutePlan } from "@/types/route.types";

const props = defineProps<{ route: RoutePlan | null }>();

const mapContainer = ref<HTMLDivElement | null>(null);
const errorMessage = ref<string | null>(null);

let map: L.Map | null = null;
let poiLayer: L.LayerGroup | null = null;
let routeLayer: L.LayerGroup | null = null;
let viewportRefreshTimer: ReturnType<typeof setTimeout> | undefined;

function poiMarkerIcon(poi: PointOfInterest): L.DivIcon {
  const color = poi.type === "restaurant" ? "#e8590c" : poi.hasRestaurant ? "#2f9e44" : "#1971c2";
  return L.divIcon({
    className: "poi-marker",
    html: `<span style="background:${color}"></span>`,
    iconSize: [16, 16],
    iconAnchor: [8, 8],
  });
}

function poiPopupHtml(poi: PointOfInterest): string {
  const typeLabel = poi.type === "restaurant" ? "Restaurant" : "Gas station";
  const extra = poi.type === "gas_station" ? (poi.hasRestaurant ? "Has a restaurant" : "Cold station (fuel only)") : "";
  const address = poi.address ? `<br>${poi.address}` : "";
  return `<strong>${poi.name}</strong><br>${typeLabel}${extra ? ` &mdash; ${extra}` : ""}${address}`;
}

async function refreshPois(): Promise<void> {
  if (!map || !poiLayer) {
    return;
  }
  try {
    const pois = await fetchPoisInViewport(map.getBounds());
    poiLayer.clearLayers();
    for (const poi of pois) {
      const [lng, lat] = poi.location.coordinates;
      L.marker([lat, lng], { icon: poiMarkerIcon(poi) }).bindPopup(poiPopupHtml(poi)).addTo(poiLayer);
    }
    errorMessage.value = null;
  } catch {
    errorMessage.value = "Failed to load gas stations and restaurants.";
  }
}

function scheduleViewportRefresh(): void {
  clearTimeout(viewportRefreshTimer);
  viewportRefreshTimer = setTimeout(refreshPois, 300);
}

function startEndIcon(kind: "start" | "end"): L.DivIcon {
  const color = kind === "start" ? "#2f9e44" : "#e03131";
  return L.divIcon({
    className: "route-endpoint-marker",
    html: `<span style="background:${color}"></span>`,
    iconSize: [20, 20],
    iconAnchor: [10, 10],
  });
}

function renderRoute(route: RoutePlan | null): void {
  if (!map || !routeLayer) {
    return;
  }
  routeLayer.clearLayers();
  if (!route) {
    return;
  }

  L.polyline(route.path, { color: "#1c7ed6", weight: 5, opacity: 0.85 }).addTo(routeLayer);
  L.marker(route.start.position, { icon: startEndIcon("start") }).bindPopup(`Start: ${route.start.label}`).addTo(routeLayer);
  L.marker(route.end.position, { icon: startEndIcon("end") }).bindPopup(`End: ${route.end.label}`).addTo(routeLayer);

  map.fitBounds(L.latLngBounds(route.path), { padding: [32, 32] });
}

onMounted(() => {
  if (!mapContainer.value) {
    return;
  }

  map = L.map(mapContainer.value, {
    center: FINLAND_CENTER,
    zoom: FINLAND_DEFAULT_ZOOM,
    minZoom: FINLAND_MIN_ZOOM,
    maxBounds: FINLAND_BOUNDS,
    maxBoundsViscosity: 1,
  });

  L.tileLayer(OSM_TILE_LAYER_URL, { attribution: OSM_TILE_LAYER_ATTRIBUTION }).addTo(map);
  map.fitBounds(FINLAND_BOUNDS);

  poiLayer = L.layerGroup().addTo(map);
  routeLayer = L.layerGroup().addTo(map);

  map.on("moveend", scheduleViewportRefresh);
  void refreshPois();
});

onUnmounted(() => {
  clearTimeout(viewportRefreshTimer);
  map?.remove();
  map = null;
});

watch(() => props.route, renderRoute);
</script>

<template>
  <div class="map-wrapper">
    <div
      ref="mapContainer"
      class="map-container"
    />
    <p
      v-if="errorMessage"
      class="map-error"
    >
      {{ errorMessage }}
    </p>
  </div>
</template>

<style>
.poi-marker span,
.route-endpoint-marker span {
  display: block;
  width: 100%;
  height: 100%;
  border-radius: 50%;
  border: 2px solid white;
  box-shadow: 0 0 2px rgba(0, 0, 0, 0.6);
}
</style>

<style scoped>
.map-wrapper {
  position: relative;
  width: 100%;
  height: 100%;
}

.map-container {
  width: 100%;
  height: 100%;
}

.map-error {
  position: absolute;
  top: 0.5rem;
  left: 0.5rem;
  z-index: 1000;
  background: #fff3bf;
  padding: 0.4rem 0.75rem;
  border-radius: 4px;
  font-size: 0.85rem;
}
</style>
