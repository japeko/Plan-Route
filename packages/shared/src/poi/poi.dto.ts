import type { GeoPoint, PointOfInterest } from "./poi.types.js";

// A plain Omit<Union, K> collapses the union into its common shape, which
// loses the gas_station/restaurant discrimination. Distribute over the
// union members instead so hasRestaurant stays tied to type: "gas_station".
type DistributiveOmit<T, K extends keyof T> = T extends unknown ? Omit<T, K> : never;

export type CreatePoiDto = DistributiveOmit<PointOfInterest, "id">;

export interface UpdatePoiDto {
  name?: string;
  location?: GeoPoint;
  address?: string;
  hasRestaurant?: boolean;
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
