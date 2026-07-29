import type { FuelType, PoiType } from "./poi.types.js";

export const POI_TYPES = ["gas_station", "restaurant", "camping"] as const satisfies readonly PoiType[];

export const FUEL_TYPES = ["gasoline", "electric"] as const satisfies readonly FuelType[];

export const DEFAULT_ROUTE_FILTER_RADIUS_METERS = 500;

// Camping areas are far sparser than gas stations/restaurants, so they get
// their own, much larger, independent search radius along the route.
export const DEFAULT_CAMPING_FILTER_RADIUS_METERS = 10000;
