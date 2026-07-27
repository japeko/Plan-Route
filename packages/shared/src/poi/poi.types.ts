export type PoiType = "gas_station" | "restaurant";

export type FuelType = "gasoline" | "electric";

// GeoJSON Point: coordinates are [longitude, latitude], per the GeoJSON/Mongo
// convention — the opposite order from how lat/lng is usually spoken aloud.
export interface GeoPoint {
  type: "Point";
  coordinates: [number, number];
}

export interface GeoLineString {
  type: "LineString";
  coordinates: [number, number][];
}

export interface BasePoi {
  id: string;
  name: string;
  location: GeoPoint;
  address?: string;
}

export interface GasStationPoi extends BasePoi {
  type: "gas_station";
  // A station always offers at least one of these; "cold" (per the product
  // requirement) just means hasRestaurant is false. All 6 combinations
  // (gasoline/electric/both, each with or without a restaurant) are
  // represented by these three independent flags rather than a fixed enum.
  hasGasoline: boolean;
  hasElectricCharging: boolean;
  hasRestaurant: boolean;
}

export interface RestaurantPoi extends BasePoi {
  type: "restaurant";
}

export type PointOfInterest = GasStationPoi | RestaurantPoi;
