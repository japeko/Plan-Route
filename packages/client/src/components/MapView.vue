<script setup lang="ts">
import L from "leaflet";
import "leaflet/dist/leaflet.css";
import { onMounted, onUnmounted, ref, watch } from "vue";
import type { GasStationPoi, GeoLineString, PointOfInterest } from "@poi/shared";
import { fetchPoisAlongRoute } from "@/api/poi.api";
import {
  FINLAND_BOUNDS,
  FINLAND_CENTER,
  FINLAND_DEFAULT_ZOOM,
  FINLAND_MIN_ZOOM,
  OSM_TILE_LAYER_ATTRIBUTION,
  OSM_TILE_LAYER_URL,
} from "@/constants/map.constants";
import type { PoiFilterOptions, RoutePlan } from "@/types/route.types";

const props = defineProps<{ route: RoutePlan | null; filters: PoiFilterOptions }>();

const mapContainer = ref<HTMLDivElement | null>(null);
const errorMessage = ref<string | null>(null);

let map: L.Map | null = null;
let poiLayer: L.LayerGroup | null = null;
let routeLayer: L.LayerGroup | null = null;

function stationFillColor(poi: GasStationPoi): string {
  if (poi.hasGasoline && poi.hasElectricCharging) {
    return "#0c8599";
  }
  return poi.hasElectricCharging ? "#7048e8" : "#1971c2";
}

function poiMarkerIcon(poi: PointOfInterest): L.DivIcon {
  const fill = poi.type === "restaurant" ? "#e8590c" : stationFillColor(poi);
  const border = poi.type === "gas_station" && poi.hasRestaurant ? "#f59f00" : "#ffffff";
  return L.divIcon({
    className: "poi-marker",
    html: `<span style="background:${fill};border-color:${border}"></span>`,
    iconSize: [16, 16],
    iconAnchor: [8, 8],
  });
}

function poiPopupHtml(poi: PointOfInterest): string {
  const address = poi.address ? `<br>${poi.address}` : "";

  if (poi.type === "restaurant") {
    return `<strong>${poi.name}</strong><br>Restaurant${address}`;
  }

  const fuelParts: string[] = [];
  if (poi.hasGasoline) {
    fuelParts.push("Gasoline");
  }
  if (poi.hasElectricCharging) {
    fuelParts.push("Electric charging");
  }
  const fuelLabel = fuelParts.length > 0 ? fuelParts.join(" + ") : "Fuel";
  const restaurantLabel = poi.hasRestaurant ? "Has a restaurant" : "Cold station (no restaurant)";

  return `<strong>${poi.name}</strong><br>${fuelLabel} &mdash; ${restaurantLabel}${address}`;
}

function routeToGeoLineString(route: RoutePlan): GeoLineString {
  return {
    type: "LineString",
    coordinates: route.path.map(([lat, lng]) => [lng, lat]),
  };
}

async function refreshPoisAlongRoute(): Promise<void> {
  if (!poiLayer) {
    return;
  }

  if (!props.route) {
    poiLayer.clearLayers();
    errorMessage.value = null;
    return;
  }

  try {
    const pois = await fetchPoisAlongRoute(routeToGeoLineString(props.route), props.filters);
    poiLayer.clearLayers();
    for (const poi of pois) {
      const [lng, lat] = poi.location.coordinates;
      L.marker([lat, lng], { icon: poiMarkerIcon(poi) })
        .bindTooltip(poi.name, { direction: "top", offset: [0, -10] })
        .bindPopup(poiPopupHtml(poi))
        .addTo(poiLayer);
    }
    errorMessage.value = null;
  } catch {
    errorMessage.value = "Failed to load gas stations and restaurants along the route.";
  }
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

function viaIcon(stopNumber: number): L.DivIcon {
  return L.divIcon({
    className: "route-via-marker",
    html: `<span>${stopNumber}</span>`,
    iconSize: [22, 22],
    iconAnchor: [11, 11],
  });
}

function renderRoute(route: RoutePlan | null): void {
  if (!map || !routeLayer) {
    return;
  }
  const layer = routeLayer;
  layer.clearLayers();
  if (!route) {
    return;
  }

  const [start, ...rest] = route.stops;
  const end = rest.pop();
  const viaStops = rest;

  L.polyline(route.path, { color: "#1c7ed6", weight: 5, opacity: 0.85 }).addTo(layer);

  if (start) {
    L.marker(start.position, { icon: startEndIcon("start") }).bindPopup(`Start: ${start.label}`).addTo(layer);
  }
  viaStops.forEach((stop, index) => {
    L.marker(stop.position, { icon: viaIcon(index + 1) }).bindPopup(`Stop ${index + 1}: ${stop.label}`).addTo(layer);
  });
  if (end) {
    L.marker(end.position, { icon: startEndIcon("end") }).bindPopup(`End: ${end.label}`).addTo(layer);
  }

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

  watch(() => props.route, renderRoute);
  watch([() => props.route, () => props.filters], refreshPoisAlongRoute, { deep: true });
});

onUnmounted(() => {
  map?.remove();
  map = null;
});
</script>

<template>
  <div class="map-wrapper">
    <div
      ref="mapContainer"
      class="map-container"
    />
    <div
      v-if="route"
      class="legend"
    >
      <div>
        <span
          class="dot"
          style="background: #1971c2"
        />Gasoline
      </div>
      <div>
        <span
          class="dot"
          style="background: #7048e8"
        />Electric charging
      </div>
      <div>
        <span
          class="dot"
          style="background: #0c8599"
        />Both
      </div>
      <div>
        <span
          class="dot"
          style="background: #e8590c"
        />Restaurant
      </div>
      <div><span class="dot ring" />Station has a restaurant</div>
    </div>
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
  border-width: 2px;
  border-style: solid;
  border-color: white;
  box-shadow: 0 0 2px rgba(0, 0, 0, 0.6);
}

.route-via-marker span {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  border-radius: 50%;
  background: #1c7ed6;
  border: 2px solid white;
  box-shadow: 0 0 2px rgba(0, 0, 0, 0.6);
  color: white;
  font-size: 0.7rem;
  font-weight: 700;
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

.legend {
  position: absolute;
  bottom: 1.5rem;
  left: 0.5rem;
  z-index: 1000;
  background: white;
  padding: 0.5rem 0.75rem;
  border-radius: 6px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.2);
  font-size: 0.75rem;
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  margin-right: 0.4rem;
  border: 1px solid #adb5bd;
}

.dot.ring {
  background: white;
  border: 2px solid #f59f00;
}
</style>
