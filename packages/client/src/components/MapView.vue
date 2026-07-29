<script setup lang="ts">
import L from "leaflet";
import type { LatLngTuple } from "leaflet";
import "leaflet/dist/leaflet.css";
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import type { GasStationPoi, GeoLineString, PointOfInterest } from "@poi/shared";
import { fetchPoisAlongRoute } from "@/api/poi.api";
import {
  FINLAND_BOUNDS,
  FINLAND_CENTER,
  FINLAND_DEFAULT_ZOOM,
  FINLAND_MIN_ZOOM,
  NAVIGATION_VIEW_DISTANCE_METERS,
  OSM_TILE_LAYER_ATTRIBUTION,
  OSM_TILE_LAYER_URL,
} from "@/constants/map.constants";
import {
  GEOLOCATION_PERMISSION_DENIED,
  GeolocationError,
  distanceMeters,
  findNearestPathIndex,
  resolveCurrentStepIndex,
  sliceUpcomingPath,
  watchVehiclePosition,
} from "@/services/navigation.service";
import { speak, stopSpeaking } from "@/services/speech.service";
import type { PoiFilterOptions, RoutePlan } from "@/types/route.types";
import { formatDistance } from "@/utils/format";

const props = defineProps<{ route: RoutePlan | null; filters: PoiFilterOptions }>();
const emit = defineEmits<{ navigating: [boolean] }>();

const mapContainer = ref<HTMLDivElement | null>(null);
const errorMessage = ref<string | null>(null);

const isNavigating = ref(false);
const isFollowingRoute = ref(true);
const vehiclePosition = ref<LatLngTuple | null>(null);
const currentStepIndex = ref(0);
const navError = ref<string | null>(null);

// Gated on vehiclePosition, not just route: currentStepIndex defaults to 0,
// so without this the banner would show step 0's instruction as soon as
// navigation starts even if a real GPS fix never actually arrives.
const currentStep = computed(() =>
  vehiclePosition.value && props.route ? (props.route.steps[currentStepIndex.value] ?? null) : null,
);
const distanceToNextStep = computed(() =>
  vehiclePosition.value && currentStep.value ? distanceMeters(vehiclePosition.value, currentStep.value.location) : null,
);

let map: L.Map | null = null;
let poiLayer: L.LayerGroup | null = null;
let routeLayer: L.LayerGroup | null = null;
let vehicleMarker: L.Marker | null = null;
let stopWatchingPosition: (() => void) | null = null;

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

function vehicleIcon(): L.DivIcon {
  return L.divIcon({
    className: "vehicle-marker",
    html: `<span></span>`,
    iconSize: [18, 18],
    iconAnchor: [9, 9],
  });
}

function centerOnUpcomingRoute(position: LatLngTuple): void {
  if (!map || !props.route) {
    return;
  }
  const nearestIndex = findNearestPathIndex(props.route.path, position);
  // Anchor the view on the route itself (the nearest point on it), not the
  // raw GPS position: if the device's actual location is far from the
  // route — e.g. testing this Finland app from somewhere else entirely —
  // fitBounds would otherwise have to stretch to cover both, zooming out
  // instead of in. The vehicle marker still uses the real position.
  const snappedPosition = props.route.path[nearestIndex] ?? position;
  const upcoming = sliceUpcomingPath(props.route.path, nearestIndex, NAVIGATION_VIEW_DISTANCE_METERS);
  map.fitBounds(L.latLngBounds([snappedPosition, ...upcoming]), { padding: [40, 40] });
}

function recenterNavigation(): void {
  isFollowingRoute.value = true;
  if (vehiclePosition.value) {
    centerOnUpcomingRoute(vehiclePosition.value);
  }
}

function stopNavigation(): void {
  stopWatchingPosition?.();
  stopWatchingPosition = null;
  isNavigating.value = false;
  vehiclePosition.value = null;
  currentStepIndex.value = 0;
  vehicleMarker?.remove();
  vehicleMarker = null;
  stopSpeaking();
  emit("navigating", false);

  if (map && props.route) {
    map.fitBounds(L.latLngBounds(props.route.path), { padding: [32, 32] });
  }
}

function startNavigation(): void {
  if (!props.route || !map) {
    return;
  }
  navError.value = null;
  currentStepIndex.value = 0;
  isFollowingRoute.value = true;

  stopWatchingPosition = watchVehiclePosition(
    (position) => {
      navError.value = null;
      vehiclePosition.value = position;

      if (!vehicleMarker && map) {
        vehicleMarker = L.marker(position, { icon: vehicleIcon(), zIndexOffset: 1000 }).addTo(map);
      } else {
        vehicleMarker?.setLatLng(position);
      }

      if (props.route) {
        currentStepIndex.value = resolveCurrentStepIndex(props.route.steps, position, currentStepIndex.value);
      }

      if (isFollowingRoute.value) {
        centerOnUpcomingRoute(position);
      }
    },
    (error: GeolocationError) => {
      navError.value = error.message;
      // Permission denial is terminal — nothing will succeed until the
      // user changes it, so stop cleanly. Other errors (timeout,
      // position temporarily unavailable) are transient; watchPosition
      // keeps calling back on its own, so just surface the warning and
      // keep tracking rather than dropping the session.
      if (error.code === GEOLOCATION_PERMISSION_DENIED) {
        stopNavigation();
      }
    },
  );

  isNavigating.value = true;
  emit("navigating", true);
}

function toggleNavigation(): void {
  if (isNavigating.value) {
    stopNavigation();
  } else {
    startNavigation();
  }
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

  // A real finger/mouse press starting on the map itself is the signal to
  // stop auto-recentering — unlike Leaflet's own movestart/zoomstart
  // events, this can never be triggered by our own programmatic
  // fitBounds() calls (or any follow-up Leaflet fires internally, e.g. its
  // maxBounds correction), so there's no risk of misreading a
  // recenter-button tap as user input.
  mapContainer.value.addEventListener("pointerdown", () => {
    if (isNavigating.value) {
      isFollowingRoute.value = false;
    }
  });

  watch(() => props.route, renderRoute);
  watch([() => props.route, () => props.filters], refreshPoisAlongRoute, { deep: true });
  // currentStep changes each time currentStepIndex advances to a new
  // maneuver, so this fires exactly once per turn rather than on every
  // GPS update.
  watch(currentStep, (step) => {
    if (step) {
      speak(step.instruction);
    }
  });
  // A newly planned (or cleared) route invalidates whatever step we were
  // tracking toward, so stop rather than navigate against stale data.
  watch(
    () => props.route,
    () => {
      if (isNavigating.value) {
        stopNavigation();
      }
    },
  );
});

onUnmounted(() => {
  stopWatchingPosition?.();
  stopSpeaking();
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

    <button
      v-if="route"
      type="button"
      class="nav-toggle"
      @click="toggleNavigation"
    >
      {{ isNavigating ? "Stop navigation" : "Start navigation" }}
    </button>
    <button
      v-if="isNavigating && !isFollowingRoute"
      type="button"
      class="recenter-button"
      @click="recenterNavigation"
    >
      Recenter
    </button>
    <div
      v-if="isNavigating && currentStep"
      class="nav-banner"
    >
      <span class="nav-instruction">{{ currentStep.instruction }}</span>
      <span
        v-if="distanceToNextStep !== null"
        class="nav-distance"
      >{{ formatDistance(distanceToNextStep) }}</span>
    </div>
    <div
      v-else-if="isNavigating && !navError"
      class="nav-banner nav-waiting"
    >
      Waiting for location… (check your browser's location permission prompt)
    </div>
    <p
      v-if="navError"
      class="map-error nav-error"
    >
      {{ navError }}
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

.vehicle-marker span {
  display: block;
  width: 100%;
  height: 100%;
  border-radius: 50%;
  background: #e64980;
  border: 3px solid white;
  box-shadow: 0 0 0 2px #e64980, 0 1px 4px rgba(0, 0, 0, 0.5);
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

.nav-toggle {
  position: absolute;
  top: 0.5rem;
  right: 0.5rem;
  z-index: 1000;
  padding: 0.5rem 0.9rem;
  border: none;
  border-radius: 6px;
  background: #e64980;
  color: white;
  font-size: 0.85rem;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.3);
}

.recenter-button {
  position: absolute;
  bottom: 1.5rem;
  right: 0.5rem;
  z-index: 1000;
  padding: 0.5rem 0.9rem;
  border: none;
  border-radius: 6px;
  background: #1c7ed6;
  color: white;
  font-size: 0.85rem;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.3);
}

.nav-banner {
  position: absolute;
  top: 3.25rem;
  left: 50%;
  transform: translateX(-50%);
  z-index: 1000;
  background: #212529;
  color: white;
  padding: 0.6rem 1.2rem;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: baseline;
  gap: 0.6rem;
  max-width: min(80%, 480px);
}

.nav-instruction {
  font-size: 0.95rem;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.nav-distance {
  flex-shrink: 0;
  font-size: 0.85rem;
  color: #ffc9c9;
}

.nav-waiting {
  background: #495057;
  font-size: 0.85rem;
  font-weight: 500;
}

.nav-error {
  top: 6.25rem;
}
</style>
