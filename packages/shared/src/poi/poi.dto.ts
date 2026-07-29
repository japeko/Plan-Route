import type { FuelType, GeoLineString, GeoPoint, PointOfInterest } from "./poi.types.js";

// A plain Omit<Union, K> collapses the union into its common shape, which
// loses the gas_station/restaurant discrimination. Distribute over the
// union members instead so hasRestaurant stays tied to type: "gas_station".
type DistributiveOmit<T, K extends keyof T> = T extends unknown ? Omit<T, K> : never;

export type CreatePoiDto = DistributiveOmit<PointOfInterest, "id">;

export interface UpdatePoiDto {
  name?: string;
  location?: GeoPoint;
  address?: string;
  hasGasoline?: boolean;
  hasElectricCharging?: boolean;
  hasRestaurant?: boolean;
  hasTentSites?: boolean;
  hasCaravanSites?: boolean;
}

export interface PoiListQueryDto {
  type?: PointOfInterest["type"];
}

export interface PoiViewportQueryDto {
  minLng: number;
  minLat: number;
  maxLng: number;
  maxLat: number;
  type?: PointOfInterest["type"];
}

export interface PoiNearbyQueryDto {
  lng: number;
  lat: number;
  radiusMeters: number;
  type?: PointOfInterest["type"];
}

export interface PoiAlongRouteRequestDto {
  route: GeoLineString;
  radiusMeters: number;
  showRestaurants: boolean;
  showGasStations: boolean;
  // Gas stations matching ANY of these fuel types are included. Empty
  // array means "no gas stations match" (mirrors the checkbox UI: unchecking
  // both fuel-type boxes hides all gas stations regardless of restaurant).
  fuelTypes: FuelType[];
  onlyWithRestaurant: boolean;
  showCamping: boolean;
  // Independent from radiusMeters: camping areas are sparse enough that
  // people will drive much further off-route to reach one than they would
  // for a gas station, so this needs its own, larger, search radius.
  campingRadiusMeters: number;
}
