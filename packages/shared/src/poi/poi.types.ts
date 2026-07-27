export type PoiType = "gas_station" | "restaurant";

// GeoJSON Point: coordinates are [longitude, latitude], per the GeoJSON/Mongo
// convention — the opposite order from how lat/lng is usually spoken aloud.
export interface GeoPoint {
  type: "Point";
  coordinates: [number, number];
}

export interface BasePoi {
  id: string;
  name: string;
  location: GeoPoint;
  address?: string;
}

export interface GasStationPoi extends BasePoi {
  type: "gas_station";
  // false = cold/fuel-only station, true = has an attached restaurant.
  hasRestaurant: boolean;
}

export interface RestaurantPoi extends BasePoi {
  type: "restaurant";
}

export type PointOfInterest = GasStationPoi | RestaurantPoi;
