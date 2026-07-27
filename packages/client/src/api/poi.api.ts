import type { PointOfInterest } from "@poi/shared";
import type { LatLngBounds } from "leaflet";
import { POI_API_BASE_URL } from "@/constants/api.constants";

export class PoiApiError extends Error {}

export async function fetchPoisInViewport(bounds: LatLngBounds): Promise<PointOfInterest[]> {
  const params = new URLSearchParams({
    minLng: String(bounds.getWest()),
    minLat: String(bounds.getSouth()),
    maxLng: String(bounds.getEast()),
    maxLat: String(bounds.getNorth()),
  });

  const response = await fetch(`${POI_API_BASE_URL}/viewport?${params.toString()}`);

  if (!response.ok) {
    throw new PoiApiError(`Failed to fetch points of interest (status ${response.status}).`);
  }

  return (await response.json()) as PointOfInterest[];
}
