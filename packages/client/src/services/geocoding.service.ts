import type { LatLngTuple } from "leaflet";
import { FINLAND_COUNTRY_CODE, NOMINATIM_REVERSE_URL, NOMINATIM_SEARCH_URL } from "@/constants/api.constants";
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

// Turns a GPS fix into a human-readable label ("use my current location"),
// rather than showing raw coordinates in the address field.
export async function reverseGeocode(position: LatLngTuple): Promise<GeocodedPoint> {
  const [lat, lon] = position;
  const params = new URLSearchParams({
    format: "json",
    lat: String(lat),
    lon: String(lon),
  });

  const response = await fetch(`${NOMINATIM_REVERSE_URL}?${params.toString()}`);

  if (!response.ok) {
    throw new GeocodingError(`Reverse geocoding request failed (status ${response.status}).`);
  }

  const result = (await response.json()) as NominatimResult;

  return { label: result.display_name, position };
}
