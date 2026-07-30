import type { AccommodationCategory, FuelType, PoiType } from "./poi.types.js";

export const POI_TYPES = [
  "gas_station",
  "restaurant",
  "camping",
  "accommodation",
] as const satisfies readonly PoiType[];

export const FUEL_TYPES = ["gasoline", "electric"] as const satisfies readonly FuelType[];

export const ACCOMMODATION_CATEGORIES = ["hotel", "hostel"] as const satisfies readonly AccommodationCategory[];

export const DEFAULT_ROUTE_FILTER_RADIUS_METERS = 500;

// Camping areas and accommodation are far sparser than gas stations/
// restaurants, so they each get their own, much larger, independent
// search radius along the route.
export const DEFAULT_CAMPING_FILTER_RADIUS_METERS = 10000;
export const DEFAULT_ACCOMMODATION_FILTER_RADIUS_METERS = 5000;
