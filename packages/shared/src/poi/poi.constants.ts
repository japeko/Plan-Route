import type { FuelType, PoiType } from "./poi.types.js";

export const POI_TYPES = ["gas_station", "restaurant"] as const satisfies readonly PoiType[];

export const FUEL_TYPES = ["gasoline", "electric"] as const satisfies readonly FuelType[];

export const DEFAULT_ROUTE_FILTER_RADIUS_METERS = 500;
