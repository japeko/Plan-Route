<script setup lang="ts">
import L from "leaflet";
import type { LatLngTuple } from "leaflet";
import "leaflet/dist/leaflet.css";
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from "vue";
import type { GasStationPoi, GeoLineString, PointOfInterest } from "@poi/shared";
import { fetchConstructionZoneReportsAlongRoute, removeConstructionZoneReport, reportConstructionZone } from "@/api/constructionZone.api";
import { fetchPoisAlongRoute } from "@/api/poi.api";
import {
  FINLAND_BOUNDS,
  FINLAND_CENTER,
  FINLAND_DEFAULT_ZOOM,
  FINLAND_MIN_ZOOM,
  OSM_TILE_LAYER_ATTRIBUTION,
  OSM_TILE_LAYER_URL,
} from "@/constants/map.constants";
import {
  GEOLOCATION_PERMISSION_DENIED,
  GeolocationError,
  distanceMeters,
  findNearestPathIndex,
  navigationViewDistanceMeters,
  resolveCurrentStepIndex,
  sliceUpcomingPath,
  watchVehiclePosition,
} from "@/services/navigation.service";
import { compassError, heading, startCompass, stopCompass } from "@/services/compass.service";
import type { BoundingBox } from "@/services/digitraffic.service";
import { fetchOfficialRoadworks } from "@/services/digitraffic.service";
import { currentLanguage, speak, stopSpeaking } from "@/services/speech.service";
import type { ConstructionZone, PoiFilterOptions, RoutePlan } from "@/types/route.types";
import { formatDistance } from "@/utils/format";

const props = defineProps<{ route: RoutePlan | null; filters: PoiFilterOptions }>();
const emit = defineEmits<{ navigating: [boolean]; "add-stop": [poi: PointOfInterest] }>();

const mapContainer = ref<HTMLDivElement | null>(null);
const errorMessage = ref<string | null>(null);

const isNavigating = ref(false);
const isFollowingRoute = ref(true);
const vehiclePosition = ref<LatLngTuple | null>(null);
const vehicleSpeedKmh = ref<number | null>(null);
const currentStepIndex = ref(0);
const navError = ref<string | null>(null);
const isReportingZone = ref(false);
const zoneReportError = ref<string | null>(null);
// Collapsed by default on a narrow/mobile screen (matches App.vue's
// mobile breakpoint) since the legend's stacked rows otherwise eat
// noticeably into the already-limited map height there; desktop has
// enough room to just show it. Still toggleable either way.
const isLegendCollapsed = ref(window.innerWidth <= 640);

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
let constructionZoneLayer: L.LayerGroup | null = null;
let pendingZoneReportClickHandler: ((event: L.LeafletMouseEvent) => void) | null = null;
let vehicleMarker: L.Marker | null = null;
let stopWatchingPosition: (() => void) | null = null;

function handleViewportResize(): void {
  map?.invalidateSize();
}

// Starting/stopping navigation hides/reveals the sidebar on mobile
// (.sidebar--nav-hidden in App.vue), which changes .map-area's actual
// size without the browser window itself resizing — so the resize/
// orientationchange listeners above never fire for it, and Leaflet keeps
// rendering at its stale cached size, leaving grey space where the map
// pane just grew. Wait for Vue's DOM patch (nextTick) and one more frame
// for the resulting layout reflow to actually land before re-measuring.
function invalidateMapSizeAfterLayoutChange(): void {
  void nextTick(() => {
    requestAnimationFrame(() => {
      map?.invalidateSize();
    });
  });
}

// Also called directly by App.vue's sidebar/map drag handle, which
// changes .map-area's size the same CSS-only way navigation start/stop
// does above — same fix applies.
defineExpose({ invalidateSize: invalidateMapSizeAfterLayoutChange });

function stationFillColor(poi: GasStationPoi): string {
  if (poi.hasGasoline && poi.hasElectricCharging) {
    return "#0c8599";
  }
  return poi.hasElectricCharging ? "#7048e8" : "#1971c2";
}

// Emoji markers read at a glance without needing the popup — used for
// the sparser, more "destination"-like types, where a plain colored dot
// (still used for gas stations/restaurants below) is less recognizable.
function poiMarkerIcon(poi: PointOfInterest): L.DivIcon {
  if (poi.type === "camping") {
    return L.divIcon({
      className: "poi-emoji-marker",
      html: `<span>⛺</span>`,
      iconSize: [24, 24],
      iconAnchor: [12, 20],
    });
  }
  if (poi.type === "accommodation") {
    const emoji = poi.category === "hotel" ? "🏨" : "🛌";
    return L.divIcon({
      className: "poi-emoji-marker",
      html: `<span>${emoji}</span>`,
      iconSize: [24, 24],
      iconAnchor: [12, 20],
    });
  }

  // poi is narrowed to GasStationPoi | RestaurantPoi here.
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

  if (poi.type === "camping") {
    const siteParts: string[] = [];
    if (poi.hasTentSites) {
      siteParts.push("Tent sites");
    }
    if (poi.hasCaravanSites) {
      siteParts.push("Caravan sites");
    }
    const siteLabel = siteParts.length > 0 ? siteParts.join(" + ") : "Camping area";
    return `<strong>${poi.name}</strong><br>${siteLabel}${address}`;
  }

  if (poi.type === "accommodation") {
    const categoryLabel = poi.category === "hotel" ? "Hotel" : "Hostel";
    return `<strong>${poi.name}</strong><br>${categoryLabel}${address}`;
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

// A real DOM element (rather than an HTML string) so the "Add as stop"
// button can carry a genuine click listener that calls back into Vue,
// instead of needing a global-scope function referenced from inline
// HTML.
function poiPopupContent(poi: PointOfInterest): HTMLElement {
  const container = document.createElement("div");
  container.innerHTML = poiPopupHtml(poi);

  const addStopButton = document.createElement("button");
  addStopButton.type = "button";
  addStopButton.className = "popup-add-stop";
  addStopButton.textContent = "Add as stop";
  addStopButton.addEventListener("click", () => {
    emit("add-stop", poi);
    map?.closePopup();
  });
  container.appendChild(addStopButton);

  return container;
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
        .bindPopup(poiPopupContent(poi))
        .addTo(poiLayer);
    }
    errorMessage.value = null;
  } catch {
    errorMessage.value = "Failed to load points of interest along the route.";
  }
}

function constructionZoneIcon(): L.DivIcon {
  return L.divIcon({
    className: "construction-zone-marker",
    html: `<span>🚧</span>`,
    iconSize: [24, 24],
    iconAnchor: [12, 20],
  });
}

function constructionZonePopupContent(zone: ConstructionZone): HTMLElement {
  const container = document.createElement("div");
  const sourceLabel = zone.source === "official" ? "Official (Fintraffic)" : "User-reported";
  container.innerHTML = `<strong>Road work</strong><br>${zone.description}<br><em>${sourceLabel}</em>`;

  if (zone.source === "user_reported") {
    const removeButton = document.createElement("button");
    removeButton.type = "button";
    removeButton.className = "popup-remove-zone";
    removeButton.textContent = "Remove report";
    removeButton.addEventListener("click", () => {
      removeConstructionZoneReport(zone.id)
        .then(() => refreshConstructionZones())
        .catch(() => {
          zoneReportError.value = "Failed to remove the report.";
        });
      map?.closePopup();
    });
    container.appendChild(removeButton);
  }

  return container;
}

// Bounding box rather than the tighter route-corridor buffer used for
// POIs: roadwork markers are sparse enough nationally that this coarser
// filter (and the resulting occasional extra marker just outside the
// route) is a reasonable simplification, avoiding a server round-trip
// just for the official/read-only half of this feature.
function routeBoundingBox(route: RoutePlan): BoundingBox {
  const bounds = L.latLngBounds(route.path).pad(0.1);
  return {
    minLat: bounds.getSouth(),
    maxLat: bounds.getNorth(),
    minLng: bounds.getWest(),
    maxLng: bounds.getEast(),
  };
}

async function refreshConstructionZones(): Promise<void> {
  if (!constructionZoneLayer) {
    return;
  }

  if (!props.route) {
    constructionZoneLayer.clearLayers();
    return;
  }

  const route = props.route;
  const [officialResult, reportedResult] = await Promise.allSettled([
    fetchOfficialRoadworks(routeBoundingBox(route)),
    fetchConstructionZoneReportsAlongRoute(routeToGeoLineString(route)),
  ]);

  const zones: ConstructionZone[] = [];
  if (officialResult.status === "fulfilled") {
    zones.push(...officialResult.value);
  }
  if (reportedResult.status === "fulfilled") {
    zones.push(
      ...reportedResult.value.map(
        (report): ConstructionZone => ({
          id: report.id,
          position: [report.location.coordinates[1], report.location.coordinates[0]],
          description: "User-reported road work",
          source: "user_reported",
        }),
      ),
    );
  }

  constructionZoneLayer.clearLayers();
  for (const zone of zones) {
    L.marker(zone.position, { icon: constructionZoneIcon() })
      .bindPopup(constructionZonePopupContent(zone))
      .addTo(constructionZoneLayer);
  }
}

function toggleReportingZone(): void {
  if (!map) {
    return;
  }

  if (isReportingZone.value) {
    if (pendingZoneReportClickHandler) {
      map.off("click", pendingZoneReportClickHandler);
      pendingZoneReportClickHandler = null;
    }
    isReportingZone.value = false;
    return;
  }

  isReportingZone.value = true;
  zoneReportError.value = null;
  pendingZoneReportClickHandler = (event: L.LeafletMouseEvent) => {
    isReportingZone.value = false;
    pendingZoneReportClickHandler = null;
    reportConstructionZone({
      location: { type: "Point", coordinates: [event.latlng.lng, event.latlng.lat] },
    })
      .then(() => refreshConstructionZones())
      .catch((err: unknown) => {
        zoneReportError.value = err instanceof Error ? err.message : "Failed to report the construction zone.";
      });
  };
  map.once("click", pendingZoneReportClickHandler);
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
  const viewDistanceMeters = navigationViewDistanceMeters(map.getSize().x, vehicleSpeedKmh.value);
  const upcoming = sliceUpcomingPath(props.route.path, nearestIndex, viewDistanceMeters);
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
  vehicleSpeedKmh.value = null;
  currentStepIndex.value = 0;
  vehicleMarker?.remove();
  vehicleMarker = null;
  stopSpeaking();
  emit("navigating", false);
  invalidateMapSizeAfterLayoutChange();

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
    (position, speedKmh) => {
      navError.value = null;
      vehiclePosition.value = position;
      vehicleSpeedKmh.value = speedKmh;

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
  invalidateMapSizeAfterLayoutChange();
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
    // Default top-left position collides with the compass widget there;
    // bottom-right is the least contested corner (only briefly shares
    // space with the recenter button while navigating).
    zoomControl: false,
  });
  L.control.zoom({ position: "bottomright" }).addTo(map);

  L.tileLayer(OSM_TILE_LAYER_URL, { attribution: OSM_TILE_LAYER_ATTRIBUTION }).addTo(map);
  map.fitBounds(FINLAND_BOUNDS);

  poiLayer = L.layerGroup().addTo(map);
  routeLayer = L.layerGroup().addTo(map);
  constructionZoneLayer = L.layerGroup().addTo(map);

  // Leaflet caches the container's pixel size at creation time; if it
  // changes afterward (mobile browser chrome showing/hiding, orientation
  // change, or any layout settling shortly after mount) the map keeps
  // rendering at the stale size, leaving grey space rather than filling
  // the container. Recomputing on resize/orientation change, plus once
  // more on the next frame to catch late mount-time settling, keeps it
  // in sync.
  window.addEventListener("resize", handleViewportResize);
  window.addEventListener("orientationchange", handleViewportResize);
  requestAnimationFrame(handleViewportResize);

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
  watch(() => props.route, refreshConstructionZones);
  // currentStep changes each time currentStepIndex advances to a new
  // maneuver, so this fires exactly once per turn rather than on every
  // GPS update.
  watch(currentStep, (step) => {
    if (step) {
      speak(step.instructions[currentLanguage.value]);
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

  // Works immediately on browsers with no permission gate (Android,
  // desktop); iOS Safari requires the request to originate from a user
  // gesture, so it'll no-op here and the compass widget's own click
  // handler below is what actually grants it there.
  void startCompass();
});

onUnmounted(() => {
  stopWatchingPosition?.();
  stopSpeaking();
  stopCompass();
  window.removeEventListener("resize", handleViewportResize);
  window.removeEventListener("orientationchange", handleViewportResize);
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
    <button
      type="button"
      class="compass"
      :title="heading !== null ? `Heading: ${Math.round(heading)}°` : 'Tap to enable the compass'"
      @click="startCompass"
    >
      <span
        class="compass-needle"
        :style="{ transform: `rotate(${heading ?? 0}deg)` }"
      >
        <span class="needle-half needle-north" />
        <span class="needle-half needle-south" />
      </span>
      <span class="compass-n">N</span>
    </button>
    <p
      v-if="compassError"
      class="map-error compass-error"
    >
      {{ compassError }}
    </p>
    <div
      v-if="route"
      class="legend"
    >
      <button
        type="button"
        class="legend-toggle"
        @click="isLegendCollapsed = !isLegendCollapsed"
      >
        {{ isLegendCollapsed ? "Legend ▸" : "Legend ▾" }}
      </button>
      <template v-if="!isLegendCollapsed">
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
        <div>⛺ Camping area</div>
        <div>🏨 Hotel</div>
        <div>🛌 Hostel</div>
        <div>🚧 Road work</div>
      </template>
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
      v-if="route"
      type="button"
      class="report-zone-toggle"
      :class="{ 'report-zone-toggle--active': isReportingZone }"
      @click="toggleReportingZone"
    >
      {{ isReportingZone ? "Tap map to report…" : "🚧 Report road work" }}
    </button>
    <p
      v-if="zoneReportError"
      class="map-error zone-report-error"
    >
      {{ zoneReportError }}
    </p>
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
      <span class="nav-arrow">{{ currentStep.arrow }}</span>
      <span class="nav-instruction">{{ currentStep.roadLabel }}</span>
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

/* Leaflet's default 10px margin puts the zoom control right where the
   recenter button sits (bottom-right, see .recenter-button below) —
   push it up clear of it. */
.leaflet-control-zoom {
  margin-bottom: 4.5rem !important;
}

.popup-add-stop {
  display: block;
  margin-top: 0.5rem;
  padding: 0.3rem 0.6rem;
  border: 1px solid #1c7ed6;
  border-radius: 4px;
  background: #e7f5ff;
  color: #1c7ed6;
  font-size: 0.8rem;
  font-weight: 600;
  cursor: pointer;
}

.construction-zone-marker span,
.poi-emoji-marker span {
  display: block;
  font-size: 1.3rem;
  line-height: 1;
  text-align: center;
}

.popup-remove-zone {
  display: block;
  margin-top: 0.5rem;
  padding: 0.3rem 0.6rem;
  border: 1px solid #e03131;
  border-radius: 4px;
  background: #fff5f5;
  color: #e03131;
  font-size: 0.8rem;
  font-weight: 600;
  cursor: pointer;
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

.compass {
  position: absolute;
  top: 0.5rem;
  left: 0.5rem;
  z-index: 1000;
  width: 3rem;
  height: 3rem;
  border-radius: 50%;
  border: 1px solid #ced4da;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.3);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.compass-needle {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 0;
  height: 0;
  transform-origin: center;
}

/* Classic two-tone needle: the red half always points toward the
   current heading (north when heading is 0), the gray half the
   opposite way — unambiguous at a glance, unlike a single symmetric
   arrow glyph. */
.needle-half {
  position: absolute;
  left: -5px;
  width: 0;
  height: 0;
  border-left: 5px solid transparent;
  border-right: 5px solid transparent;
}

.needle-north {
  top: -16px;
  border-bottom: 16px solid #e03131;
}

.needle-south {
  top: 0;
  border-top: 16px solid #adb5bd;
}

.compass-n {
  position: absolute;
  top: 0.15rem;
  left: 50%;
  transform: translateX(-50%);
  font-size: 0.6rem;
  font-weight: 700;
  color: #495057;
}

.map-error {
  position: absolute;
  top: 3.5rem;
  left: 0.5rem;
  z-index: 1000;
  background: #fff3bf;
  padding: 0.4rem 0.75rem;
  border-radius: 4px;
  font-size: 0.85rem;
}

.compass-error {
  max-width: 10rem;
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

.legend-toggle {
  align-self: flex-start;
  padding: 0;
  border: none;
  background: none;
  color: #495057;
  font-size: 0.75rem;
  font-weight: 600;
  cursor: pointer;
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

.report-zone-toggle {
  position: absolute;
  top: 3.25rem;
  right: 0.5rem;
  z-index: 1000;
  padding: 0.5rem 0.9rem;
  border: none;
  border-radius: 6px;
  background: #f8f9fa;
  color: #495057;
  font-size: 0.8rem;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.3);
  white-space: nowrap;
}

.report-zone-toggle--active {
  background: #fab005;
  color: #212529;
}

.zone-report-error {
  top: 6rem;
  right: 0.5rem;
  left: auto;
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

.nav-arrow {
  flex-shrink: 0;
  font-size: 1.4rem;
  font-weight: 700;
  line-height: 1;
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
