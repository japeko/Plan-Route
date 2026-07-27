import type { GeoLineString, PoiAlongRouteRequestDto, PointOfInterest } from "@poi/shared";
import { POI_API_BASE_URL } from "@/constants/api.constants";
import type { PoiFilterOptions } from "@/types/route.types";

export class PoiApiError extends Error {}

export async function fetchPoisAlongRoute(route: GeoLineString, filters: PoiFilterOptions): Promise<PointOfInterest[]> {
  const body: PoiAlongRouteRequestDto = { route, ...filters };

  const response = await fetch(`${POI_API_BASE_URL}/along-route`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });

  if (!response.ok) {
    throw new PoiApiError(`Failed to fetch points of interest (status ${response.status}).`);
  }

  return (await response.json()) as PointOfInterest[];
}
