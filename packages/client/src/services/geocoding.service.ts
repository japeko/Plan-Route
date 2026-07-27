import type { LatLngTuple } from "leaflet";
import { FINLAND_COUNTRY_CODE, NOMINATIM_SEARCH_URL } from "@/constants/api.constants";
import type { GeocodedPoint } from "@/types/route.types";

export class GeocodingError extends Error {}

interface NominatimResult {
  lat: string;
  lon: string;
  display_name: string;
}

export async function geocodeAddressInFinland(address: string): Promise<GeocodedPoint> {
  const params = new URLSearchParams({
    format: "json",
    q: address,
    countrycodes: FINLAND_COUNTRY_CODE,
    limit: "1",
  });

  const response = await fetch(`${NOMINATIM_SEARCH_URL}?${params.toString()}`);

  if (!response.ok) {
    throw new GeocodingError(`Geocoding request failed (status ${response.status}).`);
  }

  const results = (await response.json()) as NominatimResult[];
  const match = results[0];

  if (!match) {
    throw new GeocodingError(`No location found in Finland for "${address}".`);
  }

  const position: LatLngTuple = [Number(match.lat), Number(match.lon)];

  return { label: match.display_name, position };
}
