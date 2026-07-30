import type { GeoLineString, GeoPoint } from "../poi/poi.types.js";

export interface CreateConstructionZoneReportDto {
  location: GeoPoint;
}

export interface ConstructionZonesAlongRouteRequestDto {
  route: GeoLineString;
  radiusMeters: number;
}
