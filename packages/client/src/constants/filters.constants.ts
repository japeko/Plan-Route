import { DEFAULT_CAMPING_FILTER_RADIUS_METERS, DEFAULT_ROUTE_FILTER_RADIUS_METERS, FUEL_TYPES } from "@poi/shared";
import type { PoiFilterOptions } from "@/types/route.types";

export const DEFAULT_POI_FILTERS: PoiFilterOptions = {
  radiusMeters: DEFAULT_ROUTE_FILTER_RADIUS_METERS,
  showRestaurants: true,
  showGasStations: true,
  fuelTypes: [...FUEL_TYPES],
  onlyWithRestaurant: false,
  showCamping: false,
  campingRadiusMeters: DEFAULT_CAMPING_FILTER_RADIUS_METERS,
};
